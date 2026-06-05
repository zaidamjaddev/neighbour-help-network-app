package com.example.neighbour_help_network.ui.main.dashboard

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.data.model.User
import com.example.neighbour_help_network.databinding.FragmentDashboardBinding
import com.example.neighbour_help_network.service.NHNFcmService
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

/**
 * DashboardFragment — Full-screen Google Map with radius SeekBar, SOS FAB,
 * and nearby-users count badge.
 *
 * Changes in this version:
 *  - Observes nearbyHelpers instead of raw helpers → dots filtered by radius.
 *  - Observes nearbyUsersCount → updates the badge TextView.
 *  - Falls back to requestFreshLocation() if lastLocation is null (first boot / emulator).
 */
class DashboardFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    private var googleMap: GoogleMap? = null
    private var radiusCircle: Circle? = null
    private var currentLocation: LatLng? = null
    private val helperMarkers = mutableListOf<Marker>()
    private var locationCallbackRef: LocationCallback? = null  // kept so we can remove it

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
        viewModel.startListeningToHelpers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Stop any pending location updates to prevent memory leaks
        locationCallbackRef?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallbackRef = null
        _binding = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Map setup
    // ─────────────────────────────────────────────────────────────────────────

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

        // Push zoom controls and Google logo up so they aren't hidden by the radius card (~400px)
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

        // Try cached location first (fast path)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onLocationReceived(location.latitude, location.longitude)
            } else {
                // lastLocation is null on first boot / emulator — request a fresh fix
                Log.d("Dashboard", "lastLocation null — requesting fresh fix")
                requestFreshLocation()
            }
        }.addOnFailureListener {
            Log.e("Dashboard", "lastLocation failed: ${it.message}")
            requestFreshLocation()
        }
    }

    /**
     * Requests a single high-priority location fix from the OS.
     * Used when lastLocation returns null (cold start, emulator, permissions just granted).
     */
    private fun requestFreshLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000L
        ).setMaxUpdates(1).build()  // single fix is enough

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    onLocationReceived(loc.latitude, loc.longitude)
                }
                // Remove callback after first fix to avoid battery drain
                locationCallbackRef?.let { fusedLocationClient.removeLocationUpdates(it) }
                locationCallbackRef = null
            }
        }
        locationCallbackRef = callback
        fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    /**
     * Common handler once we have a valid lat/lng — updates map UI, ViewModel, and
     * starts local Firestore notification listeners for this position.
     */
    private fun onLocationReceived(lat: Double, lng: Double) {
        if (!isAdded) return
        val latlng = LatLng(lat, lng)
        currentLocation = latlng
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latlng, 14f))
        googleMap?.addMarker(
            MarkerOptions()
                .position(latlng)
                .title("You are here")
        )
        drawRadiusCircle(latlng, (viewModel.radiusKm.value ?: 5) * 1000.0)
        // updateUserLocation saves to Firestore AND triggers radius filter
        viewModel.updateUserLocation(lat, lng)
        // Start local Firestore listeners so nearby-request notifications work
        // without needing Firebase Cloud Functions (works on free Spark plan)
        NHNFcmService.startLocalListeners(lat, lng, requireContext().applicationContext)
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

    // ─────────────────────────────────────────────────────────────────────────
    // Controls
    // ─────────────────────────────────────────────────────────────────────────

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

        val bounceIn    = AnimatorSet().apply { playTogether(scaleDownX, scaleDownY) }
        val bounceOut   = AnimatorSet().apply { playTogether(scaleUpX,   scaleUpY)   }
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

    // ─────────────────────────────────────────────────────────────────────────
    // Observers
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        // Radius label + redraw circle
        viewModel.radiusKm.observe(viewLifecycleOwner) { km ->
            binding.tvRadiusLabel.text = "$km km"
            currentLocation?.let { drawRadiusCircle(it, km * 1000.0) }
        }

        // SOS feedback
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

        // Nearby helpers (radius-filtered) → place dots on map
        viewModel.nearbyHelpers.observe(viewLifecycleOwner) { helpersList ->
            updateHelperMarkers(helpersList)
        }

        // Nearby count → update badge
        // Label switches from "online" (no GPS yet) to "in radius" (GPS locked)
        viewModel.nearbyUsersCount.observe(viewLifecycleOwner) { count ->
            val hasGps = viewModel.currentLat != null
            val label = when {
                count == 0 -> "No neighbours nearby"
                !hasGps   -> if (count == 1) "1 neighbour online" else "$count neighbours online"
                count == 1 -> "1 neighbour in radius"
                else       -> "$count neighbours in radius"
            }
            binding.tvNearbyUsersCount.text = label
            binding.tvNearbyCountBadge.text = count.toString()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Map markers
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateHelperMarkers(helpersList: List<User>) {
        helperMarkers.forEach { it.remove() }
        helperMarkers.clear()

        val helperIcon = getHelperIcon()

        helpersList.forEach { helper ->
            val lat = helper.latitude
            val lng = helper.longitude
            if (lat != null && lng != null) {
                val markerOptions = MarkerOptions()
                    .position(LatLng(lat, lng))
                    .title(helper.displayName)
                    .snippet("Volunteer / Helper — ${helper.neighborhood.ifBlank { "nearby" }}")

                if (helperIcon != null) {
                    markerOptions.icon(helperIcon)
                } else {
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                }

                googleMap?.addMarker(markerOptions)?.let { marker ->
                    helperMarkers.add(marker)
                }
            }
        }
    }

    private fun getHelperIcon(): BitmapDescriptor? {
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_helper) ?: return null
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }
}
