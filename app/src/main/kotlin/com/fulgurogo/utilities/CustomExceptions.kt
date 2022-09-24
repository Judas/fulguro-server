package com.fulgurogo.utilities

open class ApiException(message: String) : Exception(message)

object EmptyUserIdException : ApiException("Id utilisateur vide")
object InvalidUserException : ApiException("Utilisateur inconnu")