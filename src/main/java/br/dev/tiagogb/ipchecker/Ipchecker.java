package br.dev.tiagogb.ipchecker;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import static net.minecraft.server.command.CommandManager.*;

public class Ipchecker implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ip-checker");
    public static final String MOD_ID = "ip-checker";

    public static Timer timer = null;

    @Override
    public void onInitialize() {

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("checkip").executes(context -> {

                    final MinecraftServer server = context.getSource().getServer();
                    String currentIP = getCurrentIP();

                    context.getSource().sendFeedback(() -> Text.literal("O IP do servidor é: " + currentIP), false);
                    updateIP(currentIP, server);

                    return 1;
                }))
        );

        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            if (timer != null) return;

            TimerTask checkIPTask = new TimerTask() {
                public void run() {
                    updateIP(getCurrentIP(), server);
                }
            };

            timer = new Timer("br.dev.tiagogb.ip-checker.timer");
            long fiveMin = 1000 * 60 * 5;
            timer.scheduleAtFixedRate(checkIPTask, 0, fiveMin);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register((server) -> {
            if (timer == null) return;
            timer.cancel();
        });

    }

    private void updateIP(String ip, MinecraftServer server) {
        StateHandler state = StateHandler.getServerState(server);

        if (!Objects.equals(ip, state.currentServerIP)) {
            state.currentServerIP = ip;
            LOGGER.info("O IP mudou: " + ip);
            sendDiscordMsg("@here O IP mudou: " + ip);
        }
    }

    private void sendDiscordMsg(String msg) {
        StringBuilder response = new StringBuilder();
        LOGGER.debug("Enviando IP novo para o Discord.");

        try {
            URL url = new URL("https://discord.com/api/v10/channels/" + Credentials.CHANNEL_ID + "/messages");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bot " + Credentials.BOT_TOKEN);
            con.setRequestProperty("Content-Type", "application/json");

            con.setDoOutput(true);
            String body = "{\"content\": \"" + msg + "\",\"tts\": false}";
            OutputStream os = con.getOutputStream();
            byte[] input = body.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);

            int status = con.getResponseCode();

            if (status == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) response.append(inputLine);
                in.close();
            } else {
                response.append("Error in sending IP to Discord");
                LOGGER.error(response.toString());
            }

        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }

    }

    @Environment(EnvType.SERVER)
    private String getCurrentIP() {
        StringBuilder response = new StringBuilder();

        try {
            URL url = new URL("https://api.ipify.org");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            int status = con.getResponseCode();

            if (status == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) response.append(inputLine);
                in.close();

            } else {
                response.append("Error in retrieving IP");
            }

            con.disconnect();

        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }

        return response.toString();
    }
}