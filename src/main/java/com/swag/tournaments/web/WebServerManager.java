package com.swag.tournaments.web;

import com.SwagDev.SwagAPI.api.IWebService;
import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.web.handlers.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

/**
 * Registers the SwagTournaments web editor with SwagAPI's shared {@link IWebService}.
 * SwagTournaments does not own an HttpServer — SwagAPI's shared server does, and all
 * modules share its port and thread pool.
 *
 * <p>Login is handled entirely by SwagAPI's own session-cookie system: this mount point
 * ({@code /swagapi/swagtournaments/}) is already gated by SwagAPI's login before
 * {@link #handle} ever runs, so the editor has no password/token auth of its own.</p>
 */
public class WebServerManager implements HttpHandler {

    private final SwagTournaments plugin;
    private final Logger log;

    private final TemplateAPIHandler templateHandler;
    private final LiveStatusAPIHandler liveHandler;
    private final ScheduleAPIHandler scheduleHandler;
    private final HistoryAPIHandler historyHandler;
    private final ConfigAPIHandler configHandler;
    private final IntegrationsAPIHandler integrationsHandler;

    private IWebService webService;
    private boolean registered = false;

    public WebServerManager(SwagTournaments plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();

        this.templateHandler = new TemplateAPIHandler(plugin);
        this.liveHandler = new LiveStatusAPIHandler(plugin);
        this.scheduleHandler = new ScheduleAPIHandler(plugin);
        this.historyHandler = new HistoryAPIHandler(plugin);
        this.configHandler = new ConfigAPIHandler(plugin);
        this.integrationsHandler = new IntegrationsAPIHandler(plugin);
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("web-editor.enabled", true)) {
            log.info("Web editor is disabled in config.");
            return;
        }

        RegisteredServiceProvider<IWebService> rsp = Bukkit.getServicesManager().getRegistration(IWebService.class);
        if (rsp == null) {
            log.warning("Web editor is enabled but SwagAPI's IWebService is not registered "
                    + "(is SwagAPI installed?) — web editor will not be available.");
            return;
        }

        webService = rsp.getProvider();
        webService.registerModule(plugin, this);
        registered = true;

        log.info("Web editor registered — " + webService.getPluginUrl(plugin.getName().toLowerCase()));
    }

    public void stop() {
        if (!registered) return;
        if (webService != null) {
            webService.unregisterModule(plugin);
        }
        registered = false;
        log.info("Web editor unregistered.");
    }

    /** Full browser URL to the web editor, or null if it isn't registered/available. */
    public String getUrl() {
        if (!registered || webService == null) return null;
        return webService.getPluginUrl(plugin.getName().toLowerCase());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if (path.startsWith("/api/templates")) { templateHandler.handle(exchange); return; }
        if (path.startsWith("/api/live")) { liveHandler.handle(exchange); return; }
        if (path.startsWith("/api/schedule")) { scheduleHandler.handle(exchange); return; }
        if (path.startsWith("/api/history")) { historyHandler.handle(exchange); return; }
        if (path.startsWith("/api/config")) { configHandler.handle(exchange); return; }
        if (path.startsWith("/api/integrations")) { integrationsHandler.handle(exchange); return; }

        serveStatic(exchange);
    }

    private void serveStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        String file;
        if (path.equals("/") || path.isEmpty() || path.equals("/index.html")) {
            file = "index.html";
        } else {
            file = path.replaceFirst("^/", "");
        }

        file = file.replaceAll("\\.\\.", "");

        InputStream resource = getClass().getResourceAsStream("/web/" + file);
        if (resource == null) {
            JsonUtil.send(ex, 404, JsonUtil.error("File not found: " + file));
            return;
        }

        byte[] bytes = resource.readAllBytes();
        resource.close();

        String contentType = getContentType(file);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String getContentType(String filename) {
        if (filename.endsWith(".html")) return "text/html; charset=utf-8";
        if (filename.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (filename.endsWith(".css")) return "text/css; charset=utf-8";
        if (filename.endsWith(".json")) return "application/json";
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }
}
