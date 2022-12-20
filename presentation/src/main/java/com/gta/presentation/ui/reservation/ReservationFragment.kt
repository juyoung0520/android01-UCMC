package com.gta.presentation.ui.reservation

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.gta.domain.model.AvailableDate
import com.gta.domain.model.InsuranceOption
import com.gta.domain.model.PaymentOption
import com.gta.domain.model.UCMCResult
import com.gta.domain.model.toPair
import com.gta.domain.model.toPairList
import com.gta.presentation.R
import com.gta.presentation.databinding.FragmentReservationBinding
import com.gta.presentation.ui.base.BaseFragment
import com.gta.presentation.util.DateValidator
import com.gta.presentation.util.repeatOnStarted
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class ReservationFragment :
    BaseFragment<FragmentReservationBinding>(R.layout.fragment_reservation) {
    private val viewModel: ReservationViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.vm = viewModel

        setUpRadioGroup()

        initCollector()
    }

    private fun initCollector() {
        repeatOnStarted(viewLifecycleOwner) {
            viewModel.car.collectLatest { car ->
                setupDatePicker(car.availableDate, car.reservationDates)
            }
        }

        repeatOnStarted(viewLifecycleOwner) {
            viewModel.getCarRentInfoEvent.collectLatest { state ->
                binding.cpiReservationProgress.isVisible = false
                if (state is UCMCResult.Error) {
                    sendSnackBar(
                        message = getString(R.string.exception_not_found)
                    )
                }
            }
        }

        repeatOnStarted(viewLifecycleOwner) {
            viewModel.createReservationEvent.collectLatest { state ->
                when (state) {
                    is UCMCResult.Success -> {
                        binding.icLoading.root.visibility = View.VISIBLE
                        binding.btnReservationPaying.isEnabled = false
                        viewModel.payBill()
                    }
                    is UCMCResult.Error -> {
                        sendSnackBar(
                            message = getString(R.string.exception_not_found)
                        )
                    }
                }
            }
        }

        repeatOnStarted(viewLifecycleOwner) {
            viewModel.payingEvent.collectLatest { state ->
                when (state) {
                    is UCMCResult.Success -> {
                        binding.icLoading.root.visibility = View.GONE
                        findNavController().navigate(ReservationFragmentDirections.actionReservationFragmentToMapFragment())
                    }
                    is UCMCResult.Error -> {
                        sendSnackBar(
                            message = getString(R.string.exception_not_found)
                        )
                    }
                }
            }
        }

        repeatOnStarted(viewLifecycleOwner) {
            viewModel.invalidDateSelectionEvent.collectLatest { _ ->
                sendSnackBar(message = getString(R.string.invalid_date_in_selection))
                setupDatePicker(viewModel.car.value.availableDate, viewModel.car.value.reservationDates)
            }
        }
    }

    private fun setupDatePicker(
        availableDate: AvailableDate,
        reservationDates: List<AvailableDate>
    ) {
        val (startDate, endDate) = availableDate
        val dateValidator = DateValidator(availableDate.toPair(), reservationDates.toPairList())

        val constraints = CalendarConstraints.Builder()
            .setValidator(dateValidator)
            .setStart(startDate)
            .setEnd(endDate)
            .build()

        val datePicker = MaterialDatePicker.Builder
            .dateRangePicker()
            .setTheme(R.style.Theme_UCMC_DatePicker)
            .setCalendarConstraints(constraints)
            .build()

        datePicker.addOnPositiveButtonClickListener {
            viewModel.setReservationDate(AvailableDate(it.first, it.second), dateValidator)
        }

        val onClick = { _: View -> datePicker.show(childFragmentManager, null) }

        binding.ivReservationNext.setOnClickListener(onClick)
        binding.tvReservationTime.setOnClickListener(onClick)
        binding.tvReservationTotalTime.setOnClickListener(onClick)
    }

    private fun setUpRadioGroup() {
        binding.rgReservationInsuranceOptions.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rg_reservation_insurance_option_low -> {
                    viewModel.setInsuranceOption(InsuranceOption.LOW)
                }
                R.id.rg_reservation_insurance_option_midium -> {
                    viewModel.setInsuranceOption(InsuranceOption.MEDIUM)
                }
                R.id.rg_reservation_insurance_option_high -> {
                    viewModel.setInsuranceOption(InsuranceOption.HIGH)
                }
            }
        }

        binding.rgReservationPaymentOptions.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rg_reservation_payment_option_credit_card -> {
                    viewModel.setPaymentOption(PaymentOption.CREDIT_CARD)
                }
                R.id.rg_reservation_payment_option_phone -> {
                    viewModel.setPaymentOption(PaymentOption.PHONE)
                }
            }
        }
    }
}
