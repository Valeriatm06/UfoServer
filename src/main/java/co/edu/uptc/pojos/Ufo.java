package co.edu.uptc.pojos;

import java.awt.Point;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Ufo implements Serializable{

    private static final long serialVersionUID = 1L;

    private Point position;
    private double speed; 
    private double angle; 
    private boolean moving; 
    private List<Point> trajectory; 
    private double previousAngle; 
    public static final int UFO_WIDTH = 73;
    public static final int UFO_HEIGHT = 70;
    private transient Thread movementThread;

    public Ufo(Point position, double speed, double angle) {
        this.position = position;
        this.speed = speed;
        this.angle = angle;
        this.moving = true; 
        this.trajectory = new ArrayList<>(); 
        this.previousAngle = angle; 
    }

    public boolean isMoving() {
        return moving;
    }

    public void start() {
        moving = true; 
    }

    public void stop() {
        moving = false; 
    }

    public boolean isStopped() {
        return !moving; 
    }

    public void addTrajectoryPoint(Point point) {
        trajectory.add(point);
    }

    public void clearTrajectory() {
        trajectory.clear();
    }

    public boolean hasTrajectory() {
        return !trajectory.isEmpty();
    }

    public Point getNextTrajectoryPoint() {
        return hasTrajectory() ? trajectory.get(0) : null;
    }

    public void destroy() {
        if (movementThread != null && movementThread.isAlive()) {
            movementThread.interrupt();
        }
        trajectory.clear();
        this.moving = false;
    }
}
