package com.onlinelearning.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
@PropertySource(value={"file:./group4-BE/ngrok-local.properties", "file:./ngrok-local.properties"}, ignoreResourceNotFound=true)
public class LocalNgrokLauncher {
    private final ObjectMapper mapper;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    @Value("${NGROK_ENABLED:${ngrok.enabled:false}}") private boolean enabled;
    @Value("${NGROK_EXECUTABLE:${ngrok.executable:ngrok}}") private String executable="ngrok";
    @Value("${NGROK_PUBLIC_URL:${ngrok.public-url:}}") private String publicUrl="";
    private Process process;
    private Thread monitor;
    private volatile boolean stopping;

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start(ApplicationReadyEvent event) {
        if(!enabled || stopping || process!=null || !(event.getApplicationContext() instanceof ServletWebServerApplicationContext context)) return;
        int port=context.getWebServer().getPort();
        JsonNode existing=tunnels();
        if(existing!=null) {
            if(!report(existing,port)) log.warn("An ngrok agent exists on port 4040, but no HTTPS tunnel forwards to BE port {}. Check its configuration manually.",port);
            log.info("An ngrok agent is already running. BE will not create or stop that agent.");
            return;
        }
        List<String> command=new ArrayList<>(List.of(executable,"http","http://localhost:"+port));
        if(!publicUrl.isBlank()) command.addAll(List.of("--url",publicUrl));
        try {
            process=new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            log.info("Starting local ngrok for BE port {}. This exposes the BE publicly while running.",port);
            monitor=new Thread(()->awaitTunnel(port),"local-ngrok-startup");
            monitor.setDaemon(true);
            monitor.start();
        } catch(IOException e) {
            log.warn("Could not start ngrok. Install ngrok, configure its authtoken once, and check ngrok.executable. BE remains available locally.");
        }
    }

    private void awaitTunnel(int port) {
        for(int attempt=0;attempt<30 && !stopping;attempt++) {
            if(!process.isAlive()) {
                log.warn("ngrok exited with code {}. Run ngrok http {} manually to inspect the error; BE remains available locally.",process.exitValue(),port);
                return;
            }
            JsonNode result=tunnels();
            if(result!=null && report(result,port)) return;
            try { Thread.sleep(1000); }
            catch(InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        if(!stopping) log.warn("ngrok tunnel was not ready within startup timeout. Check the ngrok agent at http://127.0.0.1:4040.");
    }

    private JsonNode tunnels() {
        try {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:4040/api/tunnels"))
                .timeout(Duration.ofSeconds(1)).GET().build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==200) return mapper.readTree(response.body()).path("tunnels");
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        catch(IOException | IllegalArgumentException ignored) { /* Agent is not listening yet. */ }
        return null;
    }

    private boolean report(JsonNode tunnels,int port) {
        for(JsonNode tunnel:tunnels) {
            try {
                String address=tunnel.path("config").path("addr").asText();
                URI upstream=URI.create(address.contains("://")?address:"http://"+address);
                int upstreamPort=upstream.getPort()==-1?80:upstream.getPort();
                if(upstreamPort!=port || !List.of("localhost","127.0.0.1","::1","[::1]").contains(upstream.getHost())) continue;
                String url=tunnel.path("public_url").asText();
                if(!url.startsWith("https://")) continue;
                log.info("ngrok BE URL: {}",url);
                if(!publicUrl.isBlank() && !publicUrl.equals(url))
                    log.warn("The active ngrok URL differs from ngrok.public-url. Check SePay webhook and team FE configuration.");
                return true;
            } catch(IllegalArgumentException ignored) { /* Ignore unrelated tunnel addresses. */ }
        }
        log.debug("No HTTPS ngrok tunnel to BE port {} is ready.",port);
        return false;
    }

    @PreDestroy
    public synchronized void stop() {
        stopping=true;
        if(monitor!=null) monitor.interrupt();
        if(process!=null && process.isAlive()) {
            process.destroy();
            try { if(!process.waitFor(3,java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly(); }
            catch(InterruptedException e) { process.destroyForcibly(); Thread.currentThread().interrupt(); }
        }
    }
}
