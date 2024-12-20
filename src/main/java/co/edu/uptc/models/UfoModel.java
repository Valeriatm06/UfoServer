package co.edu.uptc.models;

import lombok.Getter;
import lombok.Setter;
import java.awt.Point;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.gson.Gson;
import co.edu.uptc.pojos.Ufo;

@Getter
@Setter
public class UfoModel{

    private List<Ufo> ufos;
    private boolean running;
    private Ufo selectedUfo;
    private int ufoCount;
    private int totalCrashedCount;
    private int totalArrivedCount;
    private int stoppedUfosCount;
    public static final int UFO_AREA_WIDTH = 1051;
    public static final int UFO_AREA_HEIGHT = 667;
    public static final int ARRIVAL_AREA_X = 800; 
    public static final int ARRIVAL_AREA_Y = 500; 
    public static final int ARRIVAL_AREA_WIDTH = 200; 
    public static final int ARRIVAL_AREA_HEIGHT = 100;
    private ExecutorService movementExecutor;
    private Server server;  

    public UfoModel(Server server) throws IOException {
        this.ufos = new CopyOnWriteArrayList<>();
        this.movementExecutor = Executors.newCachedThreadPool(); 
        totalCrashedCount = 0;
        totalArrivedCount = 0;
        stoppedUfosCount = 0;
        this.running = false;
        this.server = server;
    }

    public void startGame(int ufoNumber, double speed, int appearanceTime) {
        resetGameCounters();
        ufoCount = ufoNumber;
        ufos.clear();
        running = true;
        Random random = new Random();
        Thread thread = new Thread(() -> createAndMoveUfos(ufoNumber, random, speed, appearanceTime));
        thread.start();
    }

    private void createAndMoveUfos(int ufoNumber, Random random, double speed, int appearanceTime) {
        for (int i = 0; i < ufoNumber; i++) {
            if (!running) break;
            Ufo newUfo = createNewUfo(random, speed);
            synchronized (ufos) {
                ufos.add(newUfo);
            }
            updateUfosList();  
            movementExecutor.submit(() -> startUfoMovement(newUfo)); 
            sleepBetweenAppearances(appearanceTime);
        }
    }
    
    private Ufo createNewUfo(Random random, double speed){
        Point initialPosition = new Point(random.nextInt(UFO_AREA_WIDTH), random.nextInt(UFO_AREA_HEIGHT));
        double angle = random.nextDouble() * 360;
        return new Ufo(initialPosition, speed, angle);
    }

    private void updateUfosList(){
        try {
            Gson gson = new Gson();
            String ufosJson = gson.toJson(ufos);
            System.out.println("Enviando UFOs JSON: " + ufosJson);
            server.sendMessageToAllClients("UP_DATE_UFOS " + ufosJson);
        } catch (Exception e) {
            System.err.println("Error al convertir UFOs a JSON: " + e.getMessage());
        }
        
    }
    
    private void sleepBetweenAppearances(int appearanceTime){
        try {
            Thread.sleep(appearanceTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    
    public void startUfoMovement(Ufo ufo) {
        Thread movementThread = new Thread(() -> {
            try {
                while (running && ufo.isMoving()) {
                    updateUfoAndCheckCollisions(ufo);
                    Thread.sleep(100);  
                }
            } catch (InterruptedException e) {
                handleInterruption(); 
            }
        });
        movementThread.start();
    }

    private void updateUfoAndCheckCollisions(Ufo ufo) {
        synchronized (ufo) {  
            updateUfoPosition(ufo);  
            checkCollisions();  
            try {
                countMovingUfos();
            } catch (IOException e) {
                e.printStackTrace();
            }  
        }
    }

    private void handleInterruption() {
        Thread.currentThread().interrupt();
    }


    private synchronized void updateUfoPosition(Ufo ufo){
        List<Point> trajectory = ufo.getTrajectory();
        if (trajectory != null && !trajectory.isEmpty()){
            moveUfoAlongTrajectory(ufo, trajectory);
        }else {
            continueUfoMovement(ufo);
        }
        checkAndHandleOutOfBounds(ufo);
        try {
            server.sendMessageToAllClients("UP_DATE_UFOS " + new Gson().toJson(ufos));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void moveUfoAlongTrajectory(Ufo ufo, List<Point> trajectory){
        Point target = trajectory.get(0);
        double newX = target.x - ufo.getPosition().x;
        double newY = target.y - ufo.getPosition().y;
        double distance = Math.sqrt(newX * newX + newY * newY);
        if (distance < ufo.getSpeed()){
            ufo.setPosition(target);
            trajectory.remove(0);
        } else {
            moveUfoTowardsTarget(ufo, newX, newY);
        }
        ufo.setPreviousAngle(Math.atan2(newY, newX));
    }
    
    private void moveUfoTowardsTarget(Ufo ufo, double newX, double newY){
        double angleToTarget = Math.atan2(newY, newX);
        Point newPosition = new Point(
            (int) (ufo.getPosition().x + ufo.getSpeed() * Math.cos(angleToTarget)),
            (int) (ufo.getPosition().y + ufo.getSpeed() * Math.sin(angleToTarget))
        );
        ufo.setPosition(newPosition);
    }
    
    private void continueUfoMovement(Ufo ufo){
        double angleToContinue = ufo.getPreviousAngle();
        Point newPosition = new Point(
            (int) (ufo.getPosition().x + ufo.getSpeed() * Math.cos(angleToContinue)),
            (int) (ufo.getPosition().y + ufo.getSpeed() * Math.sin(angleToContinue))
        );
        ufo.setPosition(newPosition);
        ufo.setPreviousAngle(angleToContinue);
    }
    
    private void checkAndHandleOutOfBounds(Ufo ufo){
        if (isOutOfBounds(ufo.getPosition())){
            ufo.stop();
        }
    }

    public void addTrayectory(List<Point> trayectoryList){
        selectedUfo.setTrajectory(trayectoryList);
    }

    private boolean isOutOfBounds(Point position){
        return position.x <= 0 || position.x >= UFO_AREA_WIDTH || position.y <= 0 || position.y >= UFO_AREA_HEIGHT;
    }

    public int[] checkCollisions() {
        int crashedCount = 0;
        int arrivedCount = 0;
        List<Ufo> toRemove = new ArrayList<>();
        synchronized (ufos) {
            crashedCount += checkOutOfBounds(toRemove);
            crashedCount += checkCollisionsBetweenUfos(toRemove);
            arrivedCount += checkArrivals(toRemove);
            stoppedUfosCount += toRemove.size();
            removeUfos(toRemove);
        }
        updateTotals(crashedCount, arrivedCount);
        return new int[]{crashedCount, arrivedCount};
    }
    
    private void updateTotals(int crashedCount, int arrivedCount) {
        totalCrashedCount += crashedCount;
        totalArrivedCount += arrivedCount;
        updatePresenter();
    }
    
    private int checkOutOfBounds(List<Ufo> toRemove){
        int crashedCount = 0;
        for (Ufo ufo : ufos) {
            if (isOutOfBounds(ufo.getPosition())) {
                crashedCount++;
                toRemove.add(ufo);
                ufo.stop();
                ufo.destroy();
            }
        }
        return crashedCount;
    }
    
    private int checkCollisionsBetweenUfos(List<Ufo> toRemove) {
        int crashedCount = 0;
        for (Ufo ufo : ufos) {
            for (Ufo other : ufos) {
                if (ufo != other && areColliding(ufo, other)) {
                    crashedCount += handleCollision(ufo, other, toRemove);
                }
            }
        }
        return crashedCount;
    }
    
    private int handleCollision(Ufo ufo, Ufo other, List<Ufo> toRemove) {
        int crashedCount = 0;
        crashedCount += processUfoCollision(ufo, toRemove);
        crashedCount += processUfoCollision(other, toRemove);
        return crashedCount;
    }
    
    private int processUfoCollision(Ufo ufo, List<Ufo> toRemove) {
        if (!toRemove.contains(ufo)) {
            toRemove.add(ufo);
            ufo.stop();
            ufo.destroy();
            return 1;
        }
        return 0;
    }
    
    
    private int checkArrivals(List<Ufo> toRemove){
        int arrivedCount = 0;
        for (Ufo ufo : ufos) {
            if (isInArrivalArea(ufo.getPosition()) && !toRemove.contains(ufo) && !ufo.isStopped()) {
                toRemove.add(ufo);
                arrivedCount++;
                ufo.stop();
                ufo.destroy();
            }
        }
        return arrivedCount;
    }
    
    private void removeUfos(List<Ufo> toRemove){
        ufos.removeAll(toRemove);
    }
    
    private synchronized void updatePresenter() {
    if (server != null) {
        try {
            server.sendMessageToAllClients("UFO_CRASHED_COUNT " + totalCrashedCount);
            server.sendMessageToAllClients("UFO_ARRIVAL_COUNT " + totalArrivedCount);
            server.sendMessageToAllClients("UP_DATE_UFOS " + new Gson().toJson(ufos));
        } catch (IOException e) { 
            e.printStackTrace();
        }
    }
}
    
    private boolean areColliding(Ufo ufoOne, Ufo ufoTwo){
        Point pos1 = ufoOne.getPosition();
        Point pos2 = ufoTwo.getPosition();
        int collisionDistance = 40;
        double distance = pos1.distance(pos2);
        return distance < collisionDistance;
    }

    public void countMovingUfos() throws IOException{
        int count = 0;
        synchronized (ufos) {
            for (Ufo ufo : ufos) {
                if (ufo.isMoving()) {
                    count++;
                }
            }
        }
        if (server != null) {
                server.sendMessageToAllClients("UFO_MOVING_COUNT " + count);
            }
            
        }

    private boolean isInArrivalArea(Point position){
        return position.x >= ARRIVAL_AREA_X && position.x <= ARRIVAL_AREA_X + ARRIVAL_AREA_WIDTH &&
               position.y >= ARRIVAL_AREA_Y && position.y <= ARRIVAL_AREA_Y + ARRIVAL_AREA_HEIGHT;
    }

    public Ufo selectUfoAtPosition(int x, int y){
        selectedUfo = getUfoAtPosition(x, y);
        return selectedUfo;
    }
    
    private Ufo getUfoAtPosition(int x, int y){
        if (ufos != null) {
            for (Ufo ufo : ufos) {
                Point position = ufo.getPosition();
                if (isPointInUfo(x, y, position)) {
                    return ufo;
                }
            }
        }
        return null;
    }
    
    private boolean isPointInUfo(int x, int y, Point position){
        int ufoWidth = Ufo.UFO_WIDTH;
        int ufoHeight = Ufo.UFO_HEIGHT;
        return x >= position.x && x <= position.x + ufoWidth && y >= position.y && y <= position.y + ufoHeight;
    }
    

    
    public void changeSelectedUfoSpeed(int delta) throws IOException{
        if (selectedUfo != null) {
            double newSpeed = Math.max(0, selectedUfo.getSpeed() + delta);
            selectedUfo.setSpeed(newSpeed);
            synchronized (selectedUfo) { 
                if (server != null) {
                        server.sendMessageToAllClients("UP_DATE_SPEED " + newSpeed);
                }
            }
        }
    }
    
    public boolean allUfosStopped(){
        if (stoppedUfosCount >= ufoCount) {
            return true;
        }
        return false;
    }

    public void resetGameCounters(){
        totalCrashedCount = 0;
        totalArrivedCount = 0;
        stoppedUfosCount = 0;
    }    

    public Ufo getSelectedUfo(){
        return selectedUfo;
    }
    
    public boolean isRunning(){
        return running;
    }

    
    public List<Ufo> getUfosList(){
        return ufos;
    }
}