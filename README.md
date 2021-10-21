# jboomerang
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.kayr/jboomerang/badge.svg?style=plastic)](https://maven-badges.herokuapp.com/maven-central/io.github.kayr/jboomerang)

A java thread safe, functional, automatic thread local resource manager. 
.
When asked to create a new resource... it creates and stores it. When asked to create the resource again.. it returns the same resource it created before(hence the term Boomerang) unless explicitly asked to create a new one.


When the thread or function scope completes execution. The resource is closed off and cleared from the thread resource cache.


This is useful when when you need to implement complex things like automatic transaction management. One transaction will be created per thread. 

Hovewever you can decide to create a new transaction on top of the existing one. After the last transaction completes the resource falls back to the previous transaction until there are no more.

