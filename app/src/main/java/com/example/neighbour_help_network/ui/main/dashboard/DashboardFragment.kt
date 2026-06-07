package com.example.neighbour_help_network.ui.main.dashboard

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.neighbour_help_network.R
import com.example.neighbour_help_network.data.model.User
import com.example.neighbour_help_network.databinding.FragmentDashboardBinding
import com.example.neighbour_help_network.service.NHNFcmService
import com.example.neighbour_help_network.ui.main.MainActivity
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*

class DashboardFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    private var googleMap: GoogleMap? = null
    private var radiusCircle: Circle? = null
    private var currentLocation: LatLng? = null
    private val helperMarkers = mutableListOf<Marker>()
    private var locationCallbackRef: LocationCallback? = null

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupMap()
        setupSeekBar()
        setupSosFab()
        setupLeaderboardShortcut()
        observeViewModel()
        viewModel.startListeningToHelpers()
        viewModel.startListeningToKarma()
    }

    private fun setupToolbar() {
        val mainActivity = activity as? MainActivity ?: return
        binding.toolbarDashboard.title = getString(R.string.app_name)
        binding.toolbarDashboard.setNavigationIcon(R.drawable.ic_home)
        binding.toolbarDashboard.setNavigationOnClickListener {
            mainActivity.openDrawer()
        }
    }

    private fun setupLeaderboardShortcut() {
        binding.cardKarma.setOnClickListener {
            if (isAdded) {
                val navController = findNavController()
                if (navController.currentDestination?.id == R.id.dashboardFragment) {
                    navController.navigate(R.id.leaderboardFragment)
                }
            }
        }
    }

    private fun setupMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isMyLocationButtonEnabled = false
        map.setPadding(0, 0, 0, 400)
        requestLocationAndCenter()
    }

    private fun requestLocationAndCenter() {
        val ctx = context ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMapLocation()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
        }
    }

    private fun enableMapLocation() {
        val ctx = context ?: return
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        googleMap?.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (isAdded) {
                location?.let { onLocationReceived(it.latitude, it.longitude) } ?: requestFreshLocation()
            }
        }
    }

    private fun requestFreshLocation() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).setMaxUpdates(1).build()
        locationCallbackRef = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (isAdded) {
                    result.lastLocation?.let { onLocationReceived(it.latitude, it.longitude) }
                }
                locationCallbackRef?.let { fusedLocationClient.removeLocationUpdates(it) }
            }
        }
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallbackRef!!, Looper.getMainLooper())
        } catch (e: SecurityException) { }
    }

    private fun onLocationReceived(lat: Double, lng: Double) {
        if (!isAdded) return
        val latlng = LatLng(lat, lng)
        currentLocation = latlng
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latlng, 14f))
        drawRadiusCircle(latlng, (viewModel.radiusKm.value ?: 5) * 1000.0)
        viewModel.updateUserLocation(lat, lng)
        context?.applicationContext?.let { NHNFcmService.startLocalListeners(lat, lng, it) }
        
        _binding?.tvMapStatusText?.text = "Location Active"
    }

    private fun drawRadiusCircle(center: LatLng, radiusMeters: Double) {
        val ctx = context ?: return
        radiusCircle?.remove()
        radiusCircle = googleMap?.addCircle(CircleOptions().center(center).radius(radiusMeters)
            .strokeColor(ContextCompat.getColor(ctx, R.color.colorPrimary))
            .fillColor(0x152563EB).strokeWidth(3f))
    }

    private fun setupSeekBar() {
        binding.seekbarRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, f: Boolean) { viewModel.onRadiusChanged(p + 1) }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
    }

    private fun setupSosFab() {
        binding.fabSos.setOnClickListener {
            context?.let { ctx ->
                AlertDialog.Builder(ctx)
                    .setTitle("Confirm SOS")
                    .setMessage("This will alert all nearby neighbors. Are you sure?")
                    .setPositiveButton("Send SOS") { _, _ -> 
                        currentLocation?.let { viewModel.postSosAlert(it.latitude, it.longitude) }
                    }
                    .setNegativeButton("Cancel", null).show()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.radiusKm.observe(viewLifecycleOwner) { km ->
            _binding?.let { b ->
                b.tvRadiusLabel.text = getString(R.string.radius_km_format, km)
                currentLocation?.let { drawRadiusCircle(it, km * 1000.0) }
            }
        }
        viewModel.nearbyHelpers.observe(viewLifecycleOwner) { updateHelperMarkers(it) }
        viewModel.nearbyUsersCount.observe(viewLifecycleOwner) { count ->
            _binding?.let { b ->
                b.tvNearbyUsersCount.text = getString(R.string.neighbours_in_radius, count)
                b.tvNearbyCountBadge.text = count.toString()
            }
        }

        viewModel.helpPoints.observe(viewLifecycleOwner) { points ->
            _binding?.tvKarmaPoints?.text = points.toString()
        }

        viewModel.helpBadge.observe(viewLifecycleOwner) { badge ->
            _binding?.tvHelpBadge?.text = badge
        }
    }

    private fun updateHelperMarkers(helpers: List<User>) {
        helperMarkers.forEach { it.remove() }; helperMarkers.clear()
        helpers.forEach { helper ->
            val lat = helper.latitude; val lng = helper.longitude
            if (lat != null && lng != null) {
                val marker = googleMap?.addMarker(MarkerOptions().position(LatLng(lat, lng)).title(helper.displayName))
                marker?.let { helperMarkers.add(it) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationCallbackRef?.let { fusedLocationClient.removeLocationUpdates(it) }
        _binding = null
    }
}
