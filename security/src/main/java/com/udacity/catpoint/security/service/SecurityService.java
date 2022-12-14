package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.FakeImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.AlarmStatus;
import com.udacity.catpoint.security.data.ArmingStatus;
import com.udacity.catpoint.security.data.SecurityRepository;
import com.udacity.catpoint.security.data.Sensor;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Service that receives information about changes to the security system. Responsible for
 * forwarding updates to the repository and making any decisions about changing the system state.
 *
 * This is the class that should contain most of the business logic for our system, and it is the
 * class you will be writing unit tests for.
 */
public class SecurityService {

    private FakeImageService imageService;
    private SecurityRepository securityRepository;
    private Set<StatusListener> statusListeners = new HashSet<>();

    public SecurityService(SecurityRepository securityRepository, FakeImageService imageService) {
        this.securityRepository = securityRepository;
        this.imageService = imageService;
    }

    /**
     * Sets the current arming status for the system. Changing the arming status
     * may update both the alarm status.
     * @param armingStatus
     */
    public void setArmingStatus(ArmingStatus armingStatus) {
        if (armingStatus == ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        } else if (armingStatus == ArmingStatus.ARMED_HOME && isCameraShowsCat()) {
            setAllSensorsInactive();
            setAlarmStatus(AlarmStatus.ALARM);
        } else {
            setAllSensorsInactive();
        }
        securityRepository.setArmingStatus(armingStatus);
    }

    /**
     * Checks whether camera is showing a cat.
     * @return
     */
    public boolean isCameraShowsCat() {
        return securityRepository.isCameraShowsCat();
    }

    /**
     * Internal method that resets all sensors to inactive.
     */
    private void setAllSensorsInactive() {
        ConcurrentSkipListSet<Sensor> sensors = new ConcurrentSkipListSet<>(getSensors());
        sensors.stream().forEach(sensor -> changeSensorActivationStatus(sensor, false));
    }

    /**
     * Internal method that checks if all sensors are inactive.
     * @return
     */
    private boolean allSensorsAreInactive() {
        return getSensors().stream().allMatch(sensor -> !sensor.getActive());
    }


    /**
     * Internal method that handles alarm status changes based on whether
     * the camera currently shows a cat.
     * @param cat True if a cat is detected, otherwise false.
     */
    private void catDetected(Boolean cat) {
        if (cat && getArmingStatus() == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else if (!cat && allSensorsAreInactive()) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }
        statusListeners.forEach(sl -> sl.catDetected(cat));
        securityRepository.setCameraShowsCat(cat);
    }

    /**
     * Register the StatusListener for alarm system updates from within the SecurityService.
     * @param statusListener
     */
    public void addStatusListener(StatusListener statusListener) {
        statusListeners.add(statusListener);
    }

    public void removeStatusListener(StatusListener statusListener) {
        statusListeners.remove(statusListener);
    }

    /**
     * Change the alarm status of the system and notify all listeners.
     * @param status
     */
    public void setAlarmStatus(AlarmStatus status) {
        securityRepository.setAlarmStatus(status);
        statusListeners.forEach(sl -> sl.notify(status));
    }

    /**
     * Internal method for updating the alarm status when a sensor has been activated.
     */
    private void handleSensorActivated() {
        if(securityRepository.getArmingStatus() == ArmingStatus.DISARMED) {
            return; //no problem if the system is disarmed
        }
        switch(securityRepository.getAlarmStatus()) {
            case NO_ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.ALARM);
            default -> {}
        }
    }

    /**
     * Internal method for updating the alarm status when a sensor which is
     * already active has been reactivated.
     */
    private void handleSensorReactivated() {
        if (securityRepository.getAlarmStatus() == AlarmStatus.PENDING_ALARM) {
            setAlarmStatus(AlarmStatus.ALARM);
        }
    }

    /**
     * Internal method for updating the alarm status when a sensor has been deactivated
     */
    private void handleSensorDeactivated() {
        switch(securityRepository.getAlarmStatus()) {
            case PENDING_ALARM -> {
                if (allSensorsAreInactive()) {
                    setAlarmStatus(AlarmStatus.NO_ALARM); // Rule 3: Only sets to no alarm if all sensors are inactive.
                }
            }
            case ALARM -> {} // Rule 4: If alarm active, don't change alarm state
        }
    }

    /**
     * Internal method for updating the alarm status when a sensor which is
     * already inactive has been re-deactivated
     */
    private void handleSensorReDeactivated() {
        switch (securityRepository.getAlarmStatus()) {
            // This is stupid, but otherwise Mockito will be unhappy about unnecessary stubbing.
        }
    }

    /**
     * Change the activation status for the specified sensor and update alarm status if necessary.
     * @param sensor
     * @param active
     */
    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
        if(!sensor.getActive() && active) {
            sensor.setActive(true);
            handleSensorActivated(); // Rule 1: Does activating a sensor that is already active count?
        } else if (sensor.getActive() && active) {
            handleSensorReactivated();
        } else if (sensor.getActive() && !active) {
            sensor.setActive(false);
            handleSensorDeactivated(); // Rule 6: Only change state if sensor is active before deactivating
        } else if (!sensor.getActive() && !active) {
            handleSensorReDeactivated();
        }
        securityRepository.updateSensor(sensor);
    }

    /**
     * Send an image to the SecurityService for processing. The securityService will use its provided
     * ImageService to analyze the image for cats and update the alarm status accordingly.
     * @param currentCameraImage
     */
    public void processImage(BufferedImage currentCameraImage) {
        catDetected(imageService.imageContainsCat(currentCameraImage, 50.0f));
    }

    public AlarmStatus getAlarmStatus() {
        return securityRepository.getAlarmStatus();
    }

    public Set<Sensor> getSensors() {
        return securityRepository.getSensors();
    }

    public void addSensor(Sensor sensor) {
        securityRepository.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        securityRepository.removeSensor(sensor);
    }

    public ArmingStatus getArmingStatus() {
        return securityRepository.getArmingStatus();
    }
}
