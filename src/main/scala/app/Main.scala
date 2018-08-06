package app

import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.eclipse.paho.client.mqttv3.{MqttClient, MqttConnectOptions, MqttMessage}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object Main {
  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(40))

  def main(args: Array[String]): Unit = {

    val tempSensorTopic = sys.env.getOrElse("TEMP_SENSOR_TOPIC", "/NODE-4ADAF6/temp")
    val thermoCurrentTempTopic = sys.env.getOrElse("THERMO_CURRENT_TEMP_TOPIC", "/kitchenThermostat/currentTemp")
    val thermoTargetTempTopic = sys.env.getOrElse("THERMO_TARGET_TEMP_TOPIC", "/kitchenThermostat/targetTemp")
    val thermoGetTargetTempTopic = sys.env.getOrElse("THERMO_GET_TARGET_TEMP_TOPIC", "/kitchenThermostat/getTargetTemp")
    val currentHeatingCoolingTopic = sys.env.getOrElse("CURRENT_HEATING_COOLING_TOPIC", "/kitchenThermostat/currentHeatingCoolingState")

    val broker = "tcp://192.168.1.198:1883"
    val clientId = "Thermostat"
    val persistence: MemoryPersistence = new MemoryPersistence()
    var state = State(0, 0)

    Try {
      val mqttClient = new MqttClient(broker, clientId, persistence)
      val connOpts = new MqttConnectOptions()
      connOpts.setCleanSession(true)
      connOpts.setAutomaticReconnect(true)
      connOpts.setConnectionTimeout(0)
      println("Connecting to broker: " + broker)
      mqttClient.connect(connOpts)
      println("Connected")

      mqttClient.publish(thermoGetTargetTempTopic, createMessage("0"))
      publishState(mqttClient,1,0)
      mqttClient.subscribe(tempSensorTopic, 2, (topic: String, message: MqttMessage) => {
        val currentTemp = new String(message.getPayload, StandardCharsets.UTF_8).toFloat
        publishState(mqttClient, currentTemp, state.targetTemp)
        state = state.copy(currentTemp = currentTemp)
      }
      )
      mqttClient.subscribe(thermoTargetTempTopic, 2, (topic: String, message: MqttMessage) =>
        {
          val targetTemp = new String(message.getPayload, StandardCharsets.UTF_8).toFloat
          state = state.copy(targetTemp = targetTemp)
          publishState(mqttClient, state.currentTemp, targetTemp)
        })
      while (true) {
        Thread.sleep(60 * 1000)
      }
      mqttClient.disconnect()
      println("Disconnected")
    } match {
      case Success(_) => println("all good")
      case Failure(e) => println(e.getMessage)
    }

    def publishState(client: MqttClient, currentTemp: Float, targetTemp: Float): Unit = {
      client.publish(thermoCurrentTempTopic, createMessage(currentTemp.toString))
      val mode = if (currentTemp > state.targetTemp) "0" else "1"
      client.publish(currentHeatingCoolingTopic, createMessage(mode))
      println(s"currentTemp = $currentTemp targetTemp = $targetTemp heating = $mode")
    }

  }

  def createMessage(message: String): MqttMessage = {
    val m = new MqttMessage(message.getBytes)
    m.setRetained(true)
    m
  }


}

case class State(currentTemp: Float, targetTemp: Float)
