package org.example.majorgame.game;

public final class BalanceBarChallengeEngine implements ChallengeEngine {
    private final String difficulty;
    private double playerPosition;
    private double velocity;
    private double driftAcceleration;
    private double timeNeeded;
    private double targetSize;
    private double timeElapsed;
    private boolean finished;
    private boolean success;
    private double driftDirection;

    public BalanceBarChallengeEngine(String difficulty) {
        this.difficulty = difficulty != null ? difficulty.toLowerCase() : "medium";
        start();
    }

    @Override
    public void start() {
        this.playerPosition = 0.5;
        this.velocity = 0.0;
        this.timeElapsed = 0.0;
        this.finished = false;
        this.success = false;
        this.driftDirection = Math.random() < 0.5 ? -1.0 : 1.0;

        switch (difficulty) {
            case "easy" -> {
                this.driftAcceleration = 0.12;
                this.timeNeeded = 4.0;
                this.targetSize = 0.3;
            }
            case "hard" -> {
                this.driftAcceleration = 0.35;
                this.timeNeeded = 6.0;
                this.targetSize = 0.12;
            }
            default -> { // medium
                this.driftAcceleration = 0.22;
                this.timeNeeded = 5.0;
                this.targetSize = 0.2;
            }
        }
    }

    @Override
    public void update(double deltaTime) {
        if (finished) return;

        // Apply constant drift acceleration
        velocity += driftDirection * driftAcceleration * deltaTime;
        
        // Add a small random noise to drift
        velocity += (Math.random() - 0.5) * 0.15 * deltaTime;

        // Update position
        playerPosition += velocity * deltaTime;

        // Verify boundaries
        if (playerPosition < 0.0 || playerPosition > 1.0) {
            finished = true;
            success = false;
            return;
        }

        // Verify if player is in the safe zone
        if (playerPosition >= getTargetRegionStart() && playerPosition <= getTargetRegionEnd()) {
            timeElapsed += deltaTime;
            if (timeElapsed >= timeNeeded) {
                finished = true;
                success = true;
            }
        } else {
            // Decelerate or reset progress if outside safe region
            timeElapsed = Math.max(0.0, timeElapsed - deltaTime * 1.5);
        }
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public boolean isSuccess() {
        return success;
    }

    @Override
    public double getPlayerPosition() {
        return playerPosition;
    }

    @Override
    public double getTargetRegionStart() {
        return 0.5 - (targetSize / 2.0);
    }

    @Override
    public double getTargetRegionEnd() {
        return 0.5 + (targetSize / 2.0);
    }

    @Override
    public void applyImpulse(double amount) {
        if (finished) return;
        
        // Counteract drift direction if player presses keys
        velocity += amount;
        
        // Slightly nudge drift direction towards the impulse direction
        if (Math.abs(velocity) > 0.1) {
            driftDirection = velocity > 0 ? 1.0 : -1.0;
        }
    }

    public double getTimeElapsed() {
        return timeElapsed;
    }

    public double getTimeNeeded() {
        return timeNeeded;
    }
}
