package fr.nathanaudvard.emp.domain.exception

class UnauthorizedException(message: String) : Exception(message)

class ForbiddenException(message: String) : Exception(message)
