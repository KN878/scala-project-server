<h1>Scala server for a "Hidden customer" mobile application</h1>
Purely functional (and typesafe :wink:) server built with Cats/Cats-Effect, Http4S, TSEC, Doobie and PostgreSQL.
Developed following Domain Driven Design principles and based on the Scala Pet Store project.



<h3>Main functionalities are:</h3>

* Three types of users: customer, shop owner and admin
* User authentication and authorization with TSEC
* Shop owners can create shops, update and delete their shops
* Customers can access shops list and leave feedback about each shop in form of pros, cons and some additional info
* Also, shop owner can create a "hidden customer" review for each of his shops, providing actions list for it. 
* Shop owner can put some money on their account and then transact it to their shops balances.
* If "hidden customer" is enabled for a shop and there is enough money on the shop balance, then customer can access this feature and gain a nominal fee for completing it. 
