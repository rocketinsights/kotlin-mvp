package com.rocketinsights.android.ui

import android.content.pm.PackageManager
import android.net.Uri
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.onNavDestinationSelected
import coil.load
import com.rocketinsights.android.R
import com.rocketinsights.android.auth.AuthManager
import com.rocketinsights.android.databinding.FragmentMainBinding
import com.rocketinsights.android.extensions.createImageFile
import com.rocketinsights.android.extensions.getIOErrorMessage
import com.rocketinsights.android.extensions.getUriForFile
import com.rocketinsights.android.extensions.show
import com.rocketinsights.android.extensions.showToast
import com.rocketinsights.android.extensions.viewBinding
import com.rocketinsights.android.viewmodels.AuthViewModel
import com.rocketinsights.android.viewmodels.LocationResult
import com.rocketinsights.android.viewmodels.LocationViewModel
import com.rocketinsights.android.viewmodels.MainFragmentMessage
import com.rocketinsights.android.viewmodels.MainViewModel
import com.rocketinsights.android.viewmodels.PermissionsResult
import com.rocketinsights.android.viewmodels.PermissionsViewModel
import org.koin.android.ext.android.inject
import com.rocketinsights.android.viewmodels.PhotoViewModel
import org.koin.androidx.scope.ScopeFragment
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber

private const val ERROR_CREATING_IMAGE = "Error while creating temporary image file."

class MainFragment : ScopeFragment(R.layout.fragment_main) {
    private val mainViewModel: MainViewModel by viewModel()
    private val authViewModel: AuthViewModel by sharedViewModel()
    private val permissionsViewModel: PermissionsViewModel by viewModel()
    private val photoViewModel: PhotoViewModel by sharedViewModel()
    private val locationViewModel: LocationViewModel by viewModel()
    private val binding by viewBinding(FragmentMainBinding::bind)
    private val authManager: AuthManager by inject(parameters = { parametersOf(requireContext()) })
    private lateinit var loginMenuItem: MenuItem
    private lateinit var logoutMenuItem: MenuItem
    private lateinit var photoMenuItem: MenuItem
    private lateinit var getCameraImage: ActivityResultLauncher<Uri>

    private val requestGpsSwitchOn =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // We don't need to do anything
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        registerTakePictureAction()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupControls()
        setupObservers()
        updateUI()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        loginMenuItem = menu.findItem(R.id.menu_login)
        logoutMenuItem = menu.findItem(R.id.menu_logout)
        photoMenuItem = menu.findItem(R.id.menu_logout)
        hideLoginItems()
        setPhotoItemVisibility()
        observeUserLoginStatus()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.messages_fragment -> item.onNavDestinationSelected(findNavController())
            R.id.request_permissions -> {
                permissionsViewModel.requestPermissions(
                    this,
                    *LocationViewModel.LOCATION_PERMISSIONS
                )
                true
            }
            R.id.photo_fragment -> {
                takePhoto()
                true
            }
            R.id.request_location -> {
                locationViewModel.retrieveCurrentLocation()
                true
            }
            R.id.menu_login -> {
                authManager.launchSignInFlow()
                true
            }
            R.id.menu_logout -> {
                authManager.logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupControls() {
        binding.stockImage.setOnClickListener {
            val extras = FragmentNavigatorExtras(
                binding.stockImage to "stockImage"
            )
            findNavController().navigate(MainFragmentDirections.actionGrowTransition(), extras)
        }
    }

    private fun setupObservers() {
        observeMessage()
    }

    private fun observeMessage() {
        mainViewModel.message.observe(viewLifecycleOwner) { data ->
            when (data) {
                is MainFragmentMessage.Loading -> binding.message.text = getString(R.string.loading)
                is MainFragmentMessage.Success -> binding.message.text = data.message.text
                is MainFragmentMessage.Error -> binding.message.text =
                    data.exception.getIOErrorMessage(requireContext())
            }

            binding.stockImage.show()

            binding.message.setOnClickListener {
                findNavController().navigate(MainFragmentDirections.actionSlideTransition())
            }
        }

        permissionsViewModel.permissionsResult.observe(viewLifecycleOwner, { event ->
            event.getContentIfNotHandled()?.let { result ->
                when (result) {
                    is PermissionsResult.PermissionsGranted -> {
                        requireContext().showToast(getString(R.string.permissions_granted))
                    }
                    is PermissionsResult.PermissionsError -> {
                        requireContext().showToast(getString(R.string.permissions_denied))
                    }
                }
            }
        })

        locationViewModel.locationState.observe(viewLifecycleOwner, {
            when (it) {
                is LocationResult.Location -> {
                    requireContext().showToast(getString(R.string.location_current, it.latLng))
                }
                is LocationResult.PermissionsNeeded -> {
                    permissionsViewModel.requestPermissions(
                        this,
                        *LocationViewModel.LOCATION_PERMISSIONS
                    )
                }
                is LocationResult.GpsOff -> {
                    requestGpsSwitchOn.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                is LocationResult.Error -> {
                    requireContext().showToast(getString(R.string.location_error))
                }
            }
        })
    }

    private fun updateUI() {
        binding.stockImage.load(R.drawable.stock_image)
    }

    private fun hideLoginItems() {
        loginMenuItem.isVisible = false
        logoutMenuItem.isVisible = false
        loginMenuItem.isEnabled = false
        logoutMenuItem.isEnabled = false
    }

    private fun observeUserLoginStatus() {
        authViewModel.isLoggedIn.observe(viewLifecycleOwner) { isLoggedIn ->
            loginMenuItem.isVisible = !isLoggedIn
            loginMenuItem.isEnabled = !isLoggedIn
            logoutMenuItem.isVisible = isLoggedIn
            logoutMenuItem.isEnabled = isLoggedIn
        }
    }

    /**
     * Register take picture action which calls camera app.
     * Navigate to PhotoFragment to show the picture.
     */
    private fun registerTakePictureAction() {
        getCameraImage =
            registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
                if (success) {
                    photoViewModel.imageUri?.let {
                        findNavController().navigate(MainFragmentDirections.showPhotoFragment())
                    }
                } else {
                    photoViewModel.deletePhoto()
                }
            }
    }

    /**
     * Show "Take a photo" menu item only on devices which have a camera.
     */
    private fun setPhotoItemVisibility() {
        val show =
            requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        photoMenuItem.isVisible = show
        photoMenuItem.isEnabled = show
    }

    private fun takePhoto() {
        crateImageFile()
        photoViewModel.imageUri?.let {
            getCameraImage.launch(photoViewModel.imageUri)
        }
    }

    private fun crateImageFile() {
        if (photoViewModel.imageUri != null) return
        try {
            val newImageFile = requireContext().createImageFile()
            photoViewModel.imageFile = newImageFile
            photoViewModel.imageUri = requireContext().getUriForFile(newImageFile)
        } catch (e: Throwable) {
            requireContext().showToast(getString(R.string.error_creating_image_file))
            Timber.e(ERROR_CREATING_IMAGE)
        }
    }
}
