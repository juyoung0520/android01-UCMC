package com.gta.domain.usecase.reservation

import com.gta.domain.model.Reservation
import com.gta.domain.repository.ReservationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetReservationUseCase @Inject constructor(
    private val repository: ReservationRepository
) {
    operator fun invoke(reservationId: String, carId: String): Flow<Reservation> {
        return repository.getReservationInfo(reservationId, carId)
    }
}
