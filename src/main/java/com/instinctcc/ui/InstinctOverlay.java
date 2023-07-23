package com.instinctcc.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.instinctcc.InstinctPlugin;
import com.instinctcc.http.InstinctHttpService;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InstinctOverlay extends Overlay {
    private final Client client;
    private final InstinctPlugin plugin;
    private final InstinctHttpService httpService;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private String overlayText;
    private final PanelComponent panelComponent = new PanelComponent();
    @Inject
    InstinctOverlay(Client client, InstinctPlugin plugin, InstinctHttpService httpService) {
        super(plugin);
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.HIGH);
        this.client = client;
        this.plugin = plugin;
        this.httpService = httpService;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();

        // Add a header
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Instinct") // replace with actual clan name
                .build());

        if (overlayText == null) {
            fetchOverlayText();
        } else {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("")
                    .right(overlayText)
                    .build());
        }

        return panelComponent.render(graphics);
    }

    private void fetchOverlayText() {
        executorService.submit(() -> {
            try {
                String username = client.getLocalPlayer().getName();
                if (username != null && !username.isEmpty()) {
                    String url = "http://www.droptracker.io/instinct/overlay-listener.php?username=" + URLEncoder.encode(username, "UTF-8");
                    String responseJson = httpService.sendGet(url);

                    // Parse the JSON response
                    JsonObject jsonResponse = new Gson().fromJson(responseJson, JsonObject.class);
                    overlayText = jsonResponse.get("message").getAsString();
                } else {
                    overlayText = "An error occurred contacting Instinct's server.";
                }
            } catch (Exception e) {
                e.printStackTrace();
                overlayText = "Error: " + e.getMessage();
            }
        });
    }
}