package kr.ac.kaist.ir.deep.fn.obj

import kr.ac.kaist.ir.deep.fn._

/**
 * __Trait__ that describes an objective function for '''entire network'''
 *
 * Because these objective functions can be shared, we recommend to make inherited one as an object. 
 */
trait Objective extends ((ScalarMatrix, ScalarMatrix) ⇒ Scalar) with Serializable {
  /**
   * Compute differentiation value of this objective function at `x = r - o`
   *
   * @param real the expected __real output__, `r`
   * @param output the computed __output of the network__, `o`
   * @return differentiation value at `f(x)=fx`, which is __square, diagonal matrix__
   */
  def derivative(real: ScalarMatrix, output: ScalarMatrix): ScalarMatrix

  /**
   * Compute error (loss)
   *
   * @param real the expected __real output__
   * @param output the computed __output of the network__
   * @return the error
   */
  override def apply(real: ScalarMatrix, output: ScalarMatrix): Scalar
}
