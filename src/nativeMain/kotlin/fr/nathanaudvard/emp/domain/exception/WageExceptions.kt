package fr.nathanaudvard.emp.domain.exception

class LevelsTooHighException(playerId: Long) :
    Exception("Levels of player $playerId are too high to compute a laying need")
