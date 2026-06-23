package org.example.majorgame.game;

public interface ChallengeEngine {
    void start();
    void update(double deltaTime);
    boolean isFinished();
    boolean isSuccess();
    double getPlayerPosition();
    double getTargetRegionStart();
    double getTargetRegionEnd();
    void applyImpulse(double amount);
}
