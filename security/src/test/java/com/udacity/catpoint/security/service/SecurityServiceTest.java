package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.FakeImageService;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    @Mock
    private SecurityRepository securityRepository;
    @Mock
    private FakeImageService imageService;
    private SecurityService securityService;
    private Sensor sensor;

    @BeforeEach
    void init() {
        securityService = new SecurityService(securityRepository, imageService);
        sensor = new Sensor("testSensor", SensorType.DOOR);
    }
    // Test #1
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void ifArmed_andNoAlarm_andSensorActivated_setPendingAlarm(ArmingStatus armingStatus) {
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    // Test #2
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void ifArmed_andPendingAlarm_andSensorActivated_setAlarm(ArmingStatus armingStatus) {
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    // Test #3
    @Test
    void ifPendingAlarm_andAllSensorInactive_setNoAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        Sensor foo = new Sensor("foo", SensorType.DOOR);
        Sensor bar = new Sensor("bar", SensorType.DOOR);
        when(securityRepository.getSensors()).thenReturn(Set.of(
                new Sensor("foo", SensorType.DOOR),
                new Sensor("bar", SensorType.DOOR),
                sensor));
        // Trigger sensor so that all sensors are false, call handleSensorDeactivated
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // Test #4
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ifAlarm_andChangingSensorState_doNotChangeAlarmState(boolean active) {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        // Pre-set the status of sensor so to simulate a change
        sensor.setActive(!active);
        securityService.changeSensorActivationStatus(sensor, active);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    // Test #5
    @Test
    void ifSensorActivatedWhileAlreadyActive_andPendingAlarm_setAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    // Test #6
    @ParameterizedTest
    @EnumSource(AlarmStatus.class)
    void ifSensorDeactivatedWhileAlreadyInactive_doNotChangeAlarmState(AlarmStatus alarmStatus) {
        when(securityRepository.getAlarmStatus()).thenReturn(alarmStatus);
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    // Test #7
    @Test
    void ifImageServiceDetectsCat_andArmedHome_setAlarm() {
        BufferedImage fakeCat = new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(fakeCat);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    // Test #8 This is better split into two tests.
    // Test #8a: If all sensors are inactive, set no-alarm state
    @Test
    void ifImageServiceDetectsNoCat_andSensorsAllInactive_setNoAlarm() {
        BufferedImage fakeNoCat = new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);
        when(securityRepository.getSensors()).thenReturn(
                Set.of(
                        new Sensor("foo", SensorType.DOOR),
                        new Sensor("bar", SensorType.DOOR),
                        sensor
                ));
        securityService.processImage(fakeNoCat);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // Test #8b: If one of the sensor is active, do not change state
    @Test
    void ifImageServiceDetectsNoCat_andAtLeastOneSensorsActive_doNotChangeAlarmSet() {
        BufferedImage fakeNoCat = new BufferedImage(224, 224, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);
        sensor.setActive(true);
        when(securityRepository.getSensors()).thenReturn(
                Set.of(
                        new Sensor("foo", SensorType.DOOR),
                        new Sensor("bar", SensorType.DOOR),
                        sensor // This one is activated
                ));
        securityService.processImage(fakeNoCat);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    // Test #9
    @Test
    void ifDisarmed_setNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // Test #10
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void ifArmed_resetAllSensorsToInactive(ArmingStatus armingStatus) {
        Sensor foo = new Sensor("foo", SensorType.DOOR);
        Sensor bar = new Sensor("bar", SensorType.DOOR);
        foo.setActive(true);
        bar.setActive(true);
        sensor.setActive(true);
        when(securityRepository.getSensors()).thenReturn(Set.of(foo, bar, sensor));
        // Needed for handleSensorDeactivated
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.setArmingStatus(armingStatus);
        securityService.getSensors().forEach(s -> verify(securityRepository).updateSensor(s));
        securityService.getSensors().forEach(s -> assertFalse(sensor.getActive()));
    }

    // Test #11
    @Test
    void ifArmedHome_whileImageServiceHasDetectedCat_setAlarm() {
        when(securityRepository.isCameraShowsCat()).thenReturn(true);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }
}