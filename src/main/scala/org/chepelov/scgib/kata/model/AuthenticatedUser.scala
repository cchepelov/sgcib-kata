package org.chepelov.scgib.kata.model

/**
 * Represents that an authenticated user MAY
 * @param ownerId who this authenticated user is known to be
 */
case class AuthenticatedUser(ownerId: OwnerId) {
  def canActOnBehalfOf(ownerId: OwnerId): Boolean =
    ownerId == this.ownerId
}
