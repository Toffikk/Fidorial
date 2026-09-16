package fr.euphyllia.fidorial.server.management;

import fr.euphyllia.fidorial.server.ServerConfig;
import fr.fidorial.entity.GameMode;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class LiveServerSettings {

    private final AtomicBoolean autosaveEnabled = new AtomicBoolean(true);
    private final AtomicReference<String> difficulty = new AtomicReference<>("normal"); // not enforced: no difficulty system yet
    private final AtomicBoolean enforceAllowlist = new AtomicBoolean(false); // not enforced: no kick-on-removal hook wired
    private final AtomicInteger pauseWhenEmptySeconds = new AtomicInteger(0); // not enforced
    private final AtomicInteger playerIdleTimeoutSeconds = new AtomicInteger(0); // not enforced
    private final AtomicBoolean allowFlight = new AtomicBoolean(false); // not enforced: no flight/ability check hooks
    private final AtomicInteger spawnProtectionRadius = new AtomicInteger(0); // not enforced
    private final AtomicBoolean forceGameMode = new AtomicBoolean(false); // not enforced
    private final AtomicReference<GameMode> defaultGameMode;
    private final AtomicInteger simulationDistance; // not read by any tick-radius logic shown
    private final AtomicBoolean acceptTransfers = new AtomicBoolean(false); // not enforced: no transfer packet handling
    private final AtomicInteger statusHeartbeatIntervalSeconds = new AtomicInteger(5); // not enforced: no heartbeat notifier loop yet
    private final AtomicInteger operatorPermissionLevel = new AtomicInteger(4); // used as the default level for newly-opped players
    private final AtomicBoolean hideOnlinePlayers = new AtomicBoolean(false); // wireable into StatusPacketHandler's sample list
    private final AtomicBoolean statusRepliesEnabled = new AtomicBoolean(true); // wireable into StatusPacketHandler.handleStatusRequest
    private final AtomicInteger entityBroadcastRangePercent = new AtomicInteger(100); // not enforced: EntityTracker's range is fixed from sendDistance

    public LiveServerSettings(final ServerConfig config) {
        this.defaultGameMode = new AtomicReference<>(config.defaultGameMode());
        this.simulationDistance = new AtomicInteger(config.viewDistance());
    }

    public AtomicBoolean autosaveEnabled() { return autosaveEnabled; }
    public AtomicReference<String> difficulty() { return difficulty; }
    public AtomicBoolean enforceAllowlist() { return enforceAllowlist; }
    public AtomicInteger pauseWhenEmptySeconds() { return pauseWhenEmptySeconds; }
    public AtomicInteger playerIdleTimeoutSeconds() { return playerIdleTimeoutSeconds; }
    public AtomicBoolean allowFlight() { return allowFlight; }
    public AtomicInteger spawnProtectionRadius() { return spawnProtectionRadius; }
    public AtomicBoolean forceGameMode() { return forceGameMode; }
    public AtomicReference<GameMode> defaultGameMode() { return defaultGameMode; }
    public AtomicInteger simulationDistance() { return simulationDistance; }
    public AtomicBoolean acceptTransfers() { return acceptTransfers; }
    public AtomicInteger statusHeartbeatIntervalSeconds() { return statusHeartbeatIntervalSeconds; }
    public AtomicInteger operatorPermissionLevel() { return operatorPermissionLevel; }
    public AtomicBoolean hideOnlinePlayers() { return hideOnlinePlayers; }
    public AtomicBoolean statusRepliesEnabled() { return statusRepliesEnabled; }
    public AtomicInteger entityBroadcastRangePercent() { return entityBroadcastRangePercent; }
}
