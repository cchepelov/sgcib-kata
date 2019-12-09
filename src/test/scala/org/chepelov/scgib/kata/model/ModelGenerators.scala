package org.chepelov.scgib.kata.model

import org.scalacheck.Gen

object ModelGenerators {

  def genMonetaryAmount: Gen[BigDecimal] =
    for {
      cents <- Gen.chooseNum(0, 10000000)
    } yield {
      BigDecimal(cents) / 100
    }

  def genCurrency: Gen[CurrencyCode] =
    for {
      codeString <- Gen.listOfN(3, Gen.alphaUpperChar).map(_.mkString)
    } yield {
      CurrencyCode(codeString)
    }
}
