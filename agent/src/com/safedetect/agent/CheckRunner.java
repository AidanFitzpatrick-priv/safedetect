package com.safedetect.agent;

/**
 * Applies live-check failures to a sink so Detector does not list every flag twice (movement vs melee).
 */
final class CheckRunner {
    interface Sink {
        void flagged(FlagStore.Flag flag, String evidence) throws Exception;
    }

    private CheckRunner() {
    }

    static void movement(Checks combat, MoveChecks movement, Sink sink) throws Exception {
        if (combat.failedAutoBlock()) {
            sink.flagged(FlagStore.Flag.AB, combat.evidence(FlagStore.Flag.AB));
            combat.resetAutoBlock();
        }
        if (combat.failedNoSlow()) {
            sink.flagged(FlagStore.Flag.NS, combat.evidence(FlagStore.Flag.NS));
            combat.resetNoSlow();
        }
        if (combat.failedLegitScaffold()) {
            sink.flagged(FlagStore.Flag.LS, combat.evidence(FlagStore.Flag.LS));
            combat.resetLegitScaffold();
        }
        if (combat.failedAutoclicker()) {
            sink.flagged(FlagStore.Flag.AC, combat.evidence(FlagStore.Flag.AC));
            combat.resetAutoclicker();
        }
        if (movement.failedSprintScaffold()) {
            sink.flagged(FlagStore.Flag.SS, movement.evidence(FlagStore.Flag.SS));
            movement.resetSprintScaffold();
        }
        if (movement.failedTower()) {
            sink.flagged(FlagStore.Flag.TW, movement.evidence(FlagStore.Flag.TW));
            movement.resetTower();
        }
        if (movement.failedSpeed()) {
            sink.flagged(FlagStore.Flag.SP, movement.evidence(FlagStore.Flag.SP));
            movement.resetSpeed();
        }
        if (movement.failedSnapAim()) {
            sink.flagged(FlagStore.Flag.SA, movement.evidence(FlagStore.Flag.SA));
            movement.resetSnapAim();
        }
        if (movement.failedVelocity()) {
            sink.flagged(FlagStore.Flag.VL, movement.evidence(FlagStore.Flag.VL));
            movement.resetVelocity();
        }
        if (movement.failedGodBridge()) {
            sink.flagged(FlagStore.Flag.GB, movement.evidence(FlagStore.Flag.GB));
            movement.resetGodBridge();
        }
        if (movement.failedKeepY()) {
            sink.flagged(FlagStore.Flag.KY, movement.evidence(FlagStore.Flag.KY));
            movement.resetKeepY();
        }
        if (movement.failedAirScaffold()) {
            sink.flagged(FlagStore.Flag.AS, movement.evidence(FlagStore.Flag.AS));
            movement.resetAirScaffold();
        }
        if (movement.failedDiagonal()) {
            sink.flagged(FlagStore.Flag.DS, movement.evidence(FlagStore.Flag.DS));
            movement.resetDiagonal();
        }
        if (movement.failedTelly()) {
            sink.flagged(FlagStore.Flag.TL, movement.evidence(FlagStore.Flag.TL));
            movement.resetTelly();
        }
    }

    static void melee(Checks combat, Sink sink) throws Exception {
        if (combat.failedKillaura()) {
            sink.flagged(FlagStore.Flag.KA, combat.evidence(FlagStore.Flag.KA));
            combat.resetKillaura();
        }
        if (combat.failedSilentAura()) {
            sink.flagged(FlagStore.Flag.SI, combat.evidence(FlagStore.Flag.SI));
            combat.resetSilentAura();
        }
        if (combat.failedReach()) {
            sink.flagged(FlagStore.Flag.RE, combat.evidence(FlagStore.Flag.RE));
            combat.resetReach();
        }
    }
}
