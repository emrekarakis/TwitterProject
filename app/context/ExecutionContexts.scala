package context
import play.api.libs.concurrent.Akka
import play.api.Play.current
/**
 * Created by emrekarakis on 20/01/16.
 */


object ExecutionContexts {
  // 0-500 msecs
  implicit val genericOps = Akka.system.dispatchers.lookup("contexts.generic-ops")
  // 0-500 msecs
  implicit val criticalOps = Akka.system.dispatchers.lookup("contexts.critical-ops")

  // 1000 msecs-1 min
  implicit val slowIoOps = Akka.system.dispatchers.lookup("contexts.slow-io-ops")
  // More than 1 min
  implicit val slowCpuOps = Akka.system.dispatchers.lookup("contexts.slow-cpu-ops")
}