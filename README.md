<h1>Scala server for a "Hidden customer" mobile application</h1>
Purely functional (and typesafe :wink:) server built with Cats/Cats-Effect, Http4S, TSEC, Doobie and PostgreSQL.
Developed following Domain Driven Design principles and based on the Scala Pet Store project.



<h3>Main functionalities are:</h3>
1. Three types of users: customer, shop owner and admin
2. User authentication and authorization with TSEC
3. Shop owners can create shops, update and delete their shops
4. Customers can access shops list and leave feedback about each shop in form of 
pros, cons and some additional info
5. Also, shop owner can create a "hidden customer" review for each of his shops, providing actions list for it. 
6. Shop owner can put some money on their account and then transact it to their shops balances.
7. If "hidden customer" is enabled for a shop and there is enough money on the shop balance, then customer can
access this feature and gain a nominal fee for completing it. 
