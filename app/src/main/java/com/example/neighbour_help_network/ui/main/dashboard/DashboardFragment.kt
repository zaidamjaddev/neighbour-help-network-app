package com.example.neighbour_help_network.ui.main.dashboard

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.databinding.FragmentDashboardBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

/**
 * DashboardFragment — Full-screen Google Map with radius SeekBar and SOS FAB.
 */
class DashboardFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    private var googleMap: GoogleMap? = null
    private var radiusCircle: Circle? = null
    private var currentLocation: LatLng? = null

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMap()
        setupSeekBar()
        setupSosFab()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager
            .findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = false
            isMapToolbarEnabled = true
        }
        
        // Push zoom controls and Google logo up so they aren't hidden by the radius card (approx 350px)
        map.setPadding(0, 0, 0, 400)
        
        requestLocationAndCenter()
    }

    private fun requestLocationAndCenter() {
        val hasFine = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine) {
            enableMapLocation()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    private fun enableMapLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        googleMap?.isMyLocationEnabled = true
        binding.tvMapStatusText.text = "📍 Location Active"

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val latlng = LatLng(it.latitude, it.longitude)
                currentLocation = latlng
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latlng, 14f))
                googleMap?.addMarker(
                    MarkerOptions()
                        .position(latlng)
                        .title("You are here")
                )
                drawRadiusCircle(latlng, (viewModel.radiusKm.value ?: 5) * 1000.0)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            enableMapLocation()
        } else {
            binding.tvMapStatusText.text = "📍 Location denied"
            Toast.makeText(
                requireContext(),
                getString(R.string.permission_location_rationale),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun drawRadiusCircle(center: LatLng, radiusMeters: Double) {
        radiusCircle?.remove()
        radiusCircle = googleMap?.addCircle(
            CircleOptions()
                .center(center)
                .radius(radiusMeters)
                .strokeColor(ContextCompat.getColor(requireContext(), R.color.colorPrimary))
                .fillColor(0x152563EB)
                .strokeWidth(3f)
        )
    }

    private fun setupSeekBar() {
        binding.seekbarRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val km = progress + 1
                viewModel.onRadiusChanged(km)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setupSosFab() {
        binding.fabSos.setOnClickListener {
            playSosBounceAnimation()
            showSosConfirmDialog()
        }
    }

    private fun playSosBounceAnimation() {
        val fab = binding.fabSos
        val scaleDownX = ObjectAnimator.ofFloat(fab, "scaleX", 1f, 0.85f).setDuration(100)
        val scaleDownY = ObjectAnimator.ofFloat(fab, "scaleY", 1f, 0.85f).setDuration(100)
        val scaleUpX   = ObjectAnimator.ofFloat(fab, "scaleX", 0.85f, 1.20f).setDuration(150)
        val scaleUpY   = ObjectAnimator.ofFloat(fab, "scaleY", 0.85f, 1.20f).setDuration(150)
        val scaleNormX = ObjectAnimator.ofFloat(fab, "scaleX", 1.20f, 1f).setDuration(100)
        val scaleNormY = ObjectAnimator.ofFloat(fab, "scaleY", 1.20f, 1f).setDuration(100)

        val bounceIn = AnimatorSet().apply { playTogether(scaleDownX, scaleDownY) }
        val bounceOut = AnimatorSet().apply { playTogether(scaleUpX, scaleUpY) }
        val bounceSettle = AnimatorSet().apply { playTogether(scaleNormX, scaleNormY) }

        AnimatorSet().apply {
            playSequentially(bounceIn, bounceOut, bounceSettle)
            start()
        }
    }

    private fun showSosConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.sos_dialog_title))
            .setMessage(getString(R.string.sos_dialog_message))
            .setPositiveButton("🆘 Send SOS") { _, _ ->
                val loc = currentLocation ?: LatLng(0.0, 0.0)
                viewModel.postSosAlert(loc.latitude, loc.longitude)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.radiusKm.observe(viewLifecycleOwner) { km ->
            binding.tvRadiusLabel.text = "$km km"
            currentLocation?.let { drawRadiusCircle(it, km * 1000.0) }
        }

        viewModel.sosPosted.observe(viewLifecycleOwner) { posted ->
            if (posted == true) {
                Toast.makeText(requireContext(), getString(R.string.sos_sent), Toast.LENGTH_LONG).show()
            }
        }

        viewModel.sosError.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }
}
