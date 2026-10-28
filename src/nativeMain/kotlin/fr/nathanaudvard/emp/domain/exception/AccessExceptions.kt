package fr.nathanaudvard.emp.domain.exception

// A page opened without a session: the access screen says how to get one
class SignInRequiredException : Exception("A member's session is required")

class MemberLeftException : Exception("The player of the session is no longer a member of the alliance")

class UnauthorizedException(message: String) : Exception(message)

class ForbiddenException(message: String) : Exception(message)
