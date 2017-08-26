// Copyright (c) 2017 PSForever
package net.psforever.objects.guid.source

import net.psforever.objects.entity.IdentifiableEntity
import net.psforever.objects.guid.key.{LoanedKey, SecureKey}
import net.psforever.objects.guid.AvailabilityPolicy

import scala.collection.mutable

/**
  * A `NumberSource` is considered a master "pool" of numbers from which all numbers are available to be drawn.
  * The numbers are considered to be exclusive.<br>
  * <br>
  * Produce a series of numbers from 0 to a maximum number (inclusive) to be used as globally unique identifiers (GUIDs).
  * @param max the highest number to be generated by this source;
  *            must be a positive integer or zero
  * @throws IllegalArgumentException if `max` is less than zero (therefore the count of generated numbers is at most zero)
  * @throws java.lang.NegativeArraySizeException if the count of numbers generated due to max is negative
  */
class LimitedNumberSource(max : Int) extends NumberSource {
  if(max < 0) {
    throw new IllegalArgumentException(s"non-negative integers only, not $max")
  }
  private val ary : Array[Key] = Array.ofDim[Key](max + 1)
  (0 to max).foreach(x => { ary(x) = new Key })
  private var allowRestrictions : Boolean = true

  def Size : Int = ary.length

  def CountAvailable : Int = ary.count(key => key.Policy == AvailabilityPolicy.Available)

  def CountUsed : Int = ary.count(key => key.Policy != AvailabilityPolicy.Available)

  def Get(number : Int) : Option[SecureKey] = {
    if(Test(number)) {
      Some(new SecureKey(number, ary(number)))
    }
    else {
      None
    }
  }

  def Available(number : Int) : Option[LoanedKey] = {
    var out : Option[LoanedKey] = None
    if(Test(number)) {
      val key : Key = ary(number)
      if(key.Policy == AvailabilityPolicy.Available) {
        key.Policy = AvailabilityPolicy.Leased
        out = Some(new LoanedKey(number, key))
      }
    }
    out
  }

  /**
    * Consume the number of a `Monitor` and release that number from its previous assignment/use.
    * @param number the number
    * @return any object previously using this number
    */
  def Return(number : Int) : Option[IdentifiableEntity] = {
    var out : Option[IdentifiableEntity] = None
    if(Test(number)) {
      val existing : Key = ary(number)
      if(existing.Policy == AvailabilityPolicy.Leased) {
        out = existing.Object
        existing.Policy = AvailabilityPolicy.Available
        existing.Object = None
      }
    }
    out
  }

  /**
    * Produce a modifiable wrapper for the `Monitor` for this number, only if the number has not been used.
    * This wrapped `Monitor` can only be assigned once and the number may not be `Return`ed to this source.
    * @param number the number
    * @return the wrapped `Monitor`
    * @throws ArrayIndexOutOfBoundsException if the requested number is above or below the range
    */
  def Restrict(number : Int) : Option[LoanedKey] = {
    if(allowRestrictions && Test(number)) {
      val key : Key = ary(number)
      key.Policy = AvailabilityPolicy.Restricted
      Some(new LoanedKey(number, key))
    }
    else {
       None
    }
  }

  def FinalizeRestrictions : List[Int] = {
    allowRestrictions = false
    ary.zipWithIndex.filter(entry => entry._1.Policy == AvailabilityPolicy.Restricted).map(entry => entry._2).toList
  }

  def Clear() : List[IdentifiableEntity] = {
    val outList : mutable.ListBuffer[IdentifiableEntity] = mutable.ListBuffer[IdentifiableEntity]()
    for(x <- ary.indices) {
      ary(x).Policy = AvailabilityPolicy.Available
      if(ary(x).Object.isDefined) {
        outList += ary(x).Object.get
        ary(x).Object = None
      }
    }
    outList.toList
  }
}

object LimitedNumberSource {
  def apply(max : Int) : LimitedNumberSource = {
    new LimitedNumberSource(max)
  }
}
