package com.gta.presentation.ui.reservation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gta.domain.model.AvailableDate
import com.gta.domain.model.CarRentInfo
import com.gta.domain.model.InsuranceOption
import com.gta.domain.model.PaymentOption
import com.gta.domain.model.Reservation
import com.gta.domain.model.UCMCResult
import com.gta.domain.usecase.reservation.CreateReservationUseCase
import com.gta.domain.usecase.reservation.GetCarRentInfoUseCase
import com.gta.presentation.util.DateUtil
import com.gta.presentation.util.DateValidator
import com.gta.presentation.util.FirebaseUtil
import com.gta.presentation.util.MutableEventFlow
import com.gta.presentation.util.asEventFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReservationViewModel @Inject constructor(
    args: SavedStateHandle,
    getCarRentInfoUseCase: GetCarRentInfoUseCase,
    private val createReservationUseCase: CreateReservationUseCase
) : ViewModel() {
    private val carId = args.get<String>("CAR_ID")

    private val _reservationDate = MutableStateFlow<AvailableDate?>(null)
    val reservationDate: StateFlow<AvailableDate?> get() = _reservationDate

    val basePrice: StateFlow<Int> = _reservationDate.map { date ->
        val carPrice = car.value.price
        date?.let { DateUtil.getDateCount(it.start, it.end) * carPrice } ?: 0
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    private val _insuranceOption = MutableStateFlow<InsuranceOption?>(null)
    val insuranceOption: StateFlow<InsuranceOption?> get() = _insuranceOption

    private val _paymentOption = MutableStateFlow<PaymentOption?>(null)
    val paymentOption: StateFlow<PaymentOption?> get() = _paymentOption

    val totalPrice: StateFlow<Long> = basePrice.combine(insuranceOption) { base, option ->
        val optionPrice = option?.price ?: 0
        base.plus(optionPrice.toLong())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    private val _createReservationEvent = MutableEventFlow<UCMCResult<Unit>>()
    val createReservationEvent = _createReservationEvent.asEventFlow()

    private val _getCarRentInfoEvent = MutableEventFlow<UCMCResult<Unit>>()
    val getCarRentInfoEvent = _getCarRentInfoEvent.asEventFlow()

    private val _payingEvent = MutableEventFlow<UCMCResult<Unit>>()
    val payingEvent = _payingEvent.asEventFlow()

    private val _invalidDateSelectionEvent = MutableEventFlow<Boolean>()
    val invalidDateSelectionEvent = _invalidDateSelectionEvent.asEventFlow()

    val car: StateFlow<CarRentInfo> = getCarRentInfoUseCase(carId ?: "").map { result ->
        when (result) {
            is UCMCResult.Success -> {
                Result
                _getCarRentInfoEvent.emit(UCMCResult.Success(Unit))
                result.data
            }
            is UCMCResult.Error -> {
                _getCarRentInfoEvent.emit(UCMCResult.Error(result.e))
                CarRentInfo()
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CarRentInfo()
    )

    fun setReservationDate(selected: AvailableDate, dateValidator: DateValidator) {
        if (hasInvalidateInSelectedDate(selected, dateValidator)) {
            _reservationDate.value = selected
        } else {
            viewModelScope.launch {
                _invalidDateSelectionEvent.emit(true)
            }
        }
    }

    private fun hasInvalidateInSelectedDate(selected: AvailableDate, dateValidator: DateValidator): Boolean {
        var cursor = selected.start + DateUtil.DAY_TIME_UNIT
        while (cursor < selected.end) {
            if (dateValidator.isValid(cursor).not()) {
                return false
            }
            cursor += DateUtil.DAY_TIME_UNIT
        }
        return true
    }

    fun setInsuranceOption(option: InsuranceOption) {
        _insuranceOption.value = option
    }

    fun setPaymentOption(option: PaymentOption) {
        _paymentOption.value = option
    }

    fun createReservation() {
        carId ?: return
        val date = reservationDate.value?.let { date ->
            AvailableDate(
                date.start,
                date.end + 86399999
            )
        } ?: return
        val option = insuranceOption.value ?: return

        viewModelScope.launch {
            _createReservationEvent.emit(
                createReservationUseCase(
                    Reservation(
                        carId = carId,
                        lenderId = FirebaseUtil.uid,
                        ownerId = car.value.ownerId,
                        reservationDate = date,
                        price = totalPrice.value,
                        insuranceOption = option.name
                    )
                )
            )
        }
    }

    fun payBill() {
        viewModelScope.launch {
            delay(2000)
            _payingEvent.emit(UCMCResult.Success(Unit))
        }
    }
}
