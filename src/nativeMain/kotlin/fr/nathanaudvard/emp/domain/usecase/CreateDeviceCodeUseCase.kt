package fr.nathanaudvard.emp.domain.usecase

import fr.nathanaudvard.emp.domain.model.DeviceCode
import fr.nathanaudvard.emp.domain.model.Viewer
import fr.nathanaudvard.emp.service.AccessService
import kotlin.time.Clock

class CreateDeviceCodeUseCase(private val access: AccessService, private val clock: Clock) {
    suspend operator fun invoke(viewer: Viewer): DeviceCode =
        access.createDeviceCode(viewer.playerId, viewer.accessLinkId, clock.now())
}
