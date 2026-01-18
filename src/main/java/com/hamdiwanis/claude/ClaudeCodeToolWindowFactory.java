package com.hamdiwanis.claude;

import com.intellij.notification.NotificationType;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;

public class ClaudeCodeToolWindowFactory implements ToolWindowFactory {
    public static final String TOOL_WINDOW_ID = "Claude Code";
    public static final com.intellij.openapi.util.Key<ShellTerminalWidget> WIDGET_KEY =
            com.intellij.openapi.util.Key.create("CLAUDE_CODE_WIDGET");
    private static final com.intellij.openapi.util.Key<Boolean> AUTORUN_DONE_KEY =
            com.intellij.openapi.util.Key.create("CLAUDE_CODE_AUTORUN_DONE");

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = new JPanel(new BorderLayout());
        String workDir = project.getBasePath() != null ? project.getBasePath() : System.getProperty("user.home");

        // 仍用 TerminalView 创建 widget（最兼容），但随后马上隐藏底部 Terminal 工具窗
        ShellTerminalWidget widget = TerminalView.getInstance(project)
                .createLocalShellWidget(workDir, TOOL_WINDOW_ID);

        panel.add(widget.getComponent(), BorderLayout.CENTER);

        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        content.putUserData(WIDGET_KEY, widget);
        toolWindow.getContentManager().addContent(content);

        // Register ESC key handler to forward ESC to terminal (Claude Code uses ESC to interrupt)
        // JetBrains intercepts ESC by default to switch focus to editor, so we need to catch it first
        IdeEventQueue.getInstance().addDispatcher(e -> {
            if (e instanceof KeyEvent) {
                KeyEvent ke = (KeyEvent) e;
                if (ke.getID() == KeyEvent.KEY_PRESSED && ke.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
                    if (tw != null && tw.isActive()) {
                        // Send ESC character (0x1B) to the terminal
                        widget.getTerminalStarter().sendBytes(new byte[]{0x1B}, true);
                        return true; // Consume the event
                    }
                }
            }
            return false;
        }, project);

        // 关键：把底部 Terminal 工具窗收起，避免用户看到两个终端
        ToolWindow term = ToolWindowManager.getInstance(project).getToolWindow("Terminal");
        if (term != null && term.isVisible()) {
            term.hide(null);
        }

        if (Boolean.TRUE.equals(content.getUserData(AUTORUN_DONE_KEY))) return;
        content.putUserData(AUTORUN_DONE_KEY, true);

        ApplicationManager.getApplication().invokeLater(() -> autorun(project, widget, workDir));
    }

    private void autorun(Project project, ShellTerminalWidget widget, String workDir) {
        ClaudeCodeUtils.exec(project, widget, "claude");
    }
}
