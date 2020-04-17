package kn.domain

package object shops {
  implicit def createShopRequestToShop(request: CreateShopRequest): Shop =
    Shop(None, request.name, request.ownerId, 0, request.address)
}
