package com.softsynth.ksyn.math

/**
 * ChebyshevPolynomial<br>
 * Used to generate data for waveshaping table oscillators.
 *
 * @author Nick Didkovsky (C) 1997 Phil Burk and Nick Didkovsky
 */

private val twoX = Polynomial(2.0, 0.0)
private val one = Polynomial(1.0)
private val oneX = Polynomial(1.0, 0.0)

/**
 * Calculates Chebyshev polynomial of specified integer order. Recursively generated using
 * relation Tk+1(x) = 2xTk(x) - Tk-1(x)
 *
 * @return Chebyshev polynomial of specified order
 */
fun T(order: Int): Polynomial {
    return if (order == 0)
        one
    else if (order == 1)
        oneX
    else
        (T(order - 1) * twoX) - T(order - 2)
}
