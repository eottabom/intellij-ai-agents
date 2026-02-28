package io.github.eottabom.aiagents.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBuilder;
import com.intellij.ui.jcef.JBCefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JCEF 기반 AI Agents 채팅 패널
 */
public class AiAgentPanel {

    private final JPanel container;
    private final JBCefBrowser browser;
    private final JsBridge bridge;

    public AiAgentPanel(Project project, List<String> installedProviders) {
        container = new JPanel(new BorderLayout());

        JBCefClient client = JBCefApp.getInstance().createClient();
        client.setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 1);
        Disposer.register(project, client);

        browser = new JBCefBrowserBuilder()
                .setClient(client)
                .build();

        bridge = new JsBridge(browser, project);
        Disposer.register(project, bridge);

        browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
                bridge.inject();
                bridge.sendInstalledProviders(installedProviders);
                bridge.sendProjectRefs();
            }
        }, browser.getCefBrowser());

        URL indexUrl = extractWebviewToTemp();
        if (indexUrl != null) {
            browser.loadURL(indexUrl.toString());
        } else {
            browser.loadHTML(fallbackHtml());
        }

        container.add(browser.getComponent(), BorderLayout.CENTER);
    }

    public JComponent getComponent() {
        return container;
    }

    private URL extractWebviewToTemp() {
        var html = readTextResource("/webview/index.html");
        if (html == null) return null;

        try {
            var root = Files.createTempDirectory("ai-agents-webview-");
            var assetsDir = root.resolve("assets");
            Files.createDirectories(assetsDir);

            for (String relativePath : findAssetPaths(html)) {
                var contents = readResourceBytes("/webview/" + relativePath);
                if (contents == null) continue;
                var target = root.resolve(relativePath).normalize();
                if (!target.startsWith(root)) continue;
                Files.createDirectories(target.getParent());
                Files.write(target, contents);
            }

            Files.writeString(root.resolve("index.html"), html, StandardCharsets.UTF_8);
            return root.resolve("index.html").toUri().toURL();
        } catch (IOException ioException) {
            return null;
        }
    }

    private List<String> findAssetPaths(String html) {
        var paths = new ArrayList<String>();
        var matcher = Pattern.compile("(?:src|href)=[\"']\\./([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                .matcher(html);
        while (matcher.find()) paths.add(matcher.group(1));
        return paths;
    }

    private String readTextResource(String path) {
        var bytes = readResourceBytes(path);
        if (bytes == null) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] readResourceBytes(String path) {
        try (InputStream resourceInputStream = getClass().getResourceAsStream(path)) {
            if (resourceInputStream == null) return null;
            return resourceInputStream.readAllBytes();
        } catch (IOException ioException) {
            return null;
        }
    }

    private String fallbackHtml() {
        return """
                <!DOCTYPE html>
                <html>
                <body style="background:#1e1e1e; color:#ccc; font-family:sans-serif; padding:20px;">
                    <h3>AI Agents</h3>
                    <p>Run <code>npm run build</code> in <code>app/frontend/</code> to load the UI.</p>
                </body>
                </html>
                """;
    }
}
