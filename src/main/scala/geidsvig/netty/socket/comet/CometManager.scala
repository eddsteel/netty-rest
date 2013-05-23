package geidsvig.netty.socket.comet

import akka.actor.ActorRef
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.util.CharsetUtil
import akka.event.LoggingAdapter
import org.jboss.netty.channel.Channel
import akka.actor.actorRef2Scala
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.channel.ChannelFutureListener

trait CometManagerRequirements {
  val cometHandlerFactory: CometHandlerFactory
  val logger: LoggingAdapter
}

abstract class CometManager {
  this: CometManagerRequirements =>

  /**
   * Implementation can vary.
   * examples:
   * 1) singleton can store a key-value map of uuid and actorRefs
   * 2) distributed system can use guardian ring to distribute handlers
   * 3) or could use memcache to store uuid -> cometHandler actor
   */
  def handleCometRequest(request: CometRequest) {
    Option(request.request.getHeader("uuid")) match {
      case Some(uuid) => {
        // check if we have registered the handler.
        // if yes, check if actorRef is alive.
        // if yes. respond to client that they already have a connection and close the request.ctx.channel
        // if no, then create a new handler, and send it the request
        hasRegisteredHandler(uuid) match {
          case Some(handler) => {
            val response = CometPacket(HttpResponseStatus.BAD_REQUEST, "duplicate comet request for uuid")
            sendCometFrame(Option(request.ctx.getChannel()), response)
          }
          case None => {
            val handler = cometHandlerFactory.createCometHandler()
            handler ! request
          }
        }
      }
      case None => {
        val response = CometPacket(HttpResponseStatus.BAD_REQUEST, "missing uuid")
        sendCometFrame(Option(request.ctx.getChannel()), response)
      }
    }

  }

  /**
   * Simple comet push method.
   *
   * @param payload should be JSON
   */
  def sendCometFrame(channel: Option[Channel], cometPacket: CometPacket) {
    channel match {
      case Some(chan) if (chan.isOpen) => chan.write(cometPacket.toJSON).addListener(ChannelFutureListener.CLOSE)
      case Some(chan) => logger warning ("Trying to push {} with closed channel", cometPacket.toJSON)
      case None => logger warning ("Trying to push with no channel. Dropping")
    }
  }

  /**
   *
   * @param uuid
   * @returns None if handler not registered for uuid. Some(ActorRef) otherwise
   */
  def hasRegisteredHandler(uuid: String): Option[ActorRef]

  /**
   * @param uuid
   */
  def registerHandler(uuid: String)

  /**
   * Regardless of implementations, the uuid and actorRef for CometHandler should be removed.
   *
   * @param uuid
   */
  def deregisterHandler(uuid: String)

}