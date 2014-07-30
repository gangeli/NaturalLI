package org.goobs.naturalli

import org.scalatest._

/**
 * A base class for other tests
 *
 * @author gabor
 */
abstract class Test extends FunSpec with Matchers with OptionValues with Inside with Inspectors {
  Props.NATLOG_INDEXER_LAZY = true
}
