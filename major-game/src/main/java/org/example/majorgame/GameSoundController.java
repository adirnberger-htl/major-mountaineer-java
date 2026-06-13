package org.example.majorgame;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

/**
 * Manages all game sound triggers: location-based audio, combat transitions, and effects.
 * Keeps sound logic separate from UI rendering.
 */
public class GameSoundController {
    private static final String MEETING_THE_LOCAL_LOCATION_ID = "hd_homeless";

    private final SoundManager soundManager;
    private String previousLocationId;
    private boolean wasInCombat = false;

    public GameSoundController(SoundManager soundManager) {
        this.soundManager = soundManager;
    }

    /**
     * Starts the initial background music when the game launches.
     */
    public void playInitialMusic() {
        soundManager.playMusic("effects/wind.wav");
    }

    /**
     * Triggers location-specific sound sequences when the player enters a new location.
     * Only fires once per location change (ignores repeated calls with the same locationId).
     */
    public void onLocationChanged(String locationId) {
        if (locationId == null || locationId.equals(previousLocationId)) {
            return;
        }

        previousLocationId = locationId;

        // jz grad static aber ich machs später so dass es basierend auf locations
        // dynamisch dialog abspielt
        if (MEETING_THE_LOCAL_LOCATION_ID.equals(locationId)) {
            PauseTransition firstLineDelay = new PauseTransition(Duration.seconds(2.0));
            firstLineDelay.setOnFinished(event ->
                    soundManager.playEffect("meetingthelocal/meetingthelocal1.wav")
            );
            firstLineDelay.play();

            PauseTransition secondLineDelay = new PauseTransition(Duration.seconds(7.5));
            secondLineDelay.setOnFinished(event ->
                    soundManager.playEffect("meetingthelocal/meetingthelocal2.wav")
            );
            secondLineDelay.play();

            PauseTransition musicDelay = new PauseTransition(Duration.seconds(10.5));
            musicDelay.setOnFinished(event ->
                    soundManager.playMusic("meetingthelocal/backgroundmusic.wav")
            );
            musicDelay.play();
        }
    }

    /**
     * Handles sound transitions when entering or leaving combat.
     * Plays encounter sound on combat start, defeat sound on combat end.
     */
    public void onCombatStateChanged(boolean inCombat) {
        // irgwas spielen bei kampf verlassen or joinen
        if (inCombat && !wasInCombat) {
            soundManager.stopMusic();
            soundManager.playEffect("effects/enemyspotted.wav");
        } else if (!inCombat && wasInCombat) {
            soundManager.stopMusic();
            soundManager.playEffect("effects/honorloss.wav");
        }
        wasInCombat = inCombat;
    }

    /**
     * Plays the attack/punch sound effect during combat targeting.
     */
    public void playAttackSound() {
        soundManager.playEffect("effects/punch.wav");
    }
}
