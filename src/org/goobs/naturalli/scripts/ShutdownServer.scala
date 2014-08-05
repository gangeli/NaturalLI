package org.goobs.naturalli.scripts

import org.goobs.naturalli.Client

/**
 * Assue a simple shutdown request to the inference server.
 *
 * @author gabor
 */
object ShutdownServer extends Client {
  def main(args:Array[String]):Unit = shutdownServer()
}
