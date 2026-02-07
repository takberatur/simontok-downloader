package com.agcforge.videodownloader.ui.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
<<<<<<< HEAD
import androidx.recyclerview.widget.LinearLayoutManager
import com.agcforge.videodownloader.data.model.DownloadTask
import com.agcforge.videodownloader.databinding.FragmentHistoryBinding
import com.agcforge.videodownloader.helper.ads.BannerAdsHelper
import com.agcforge.videodownloader.ui.adapter.HistoryAdapter
import com.agcforge.videodownloader.helper.ads.BillingManager
import com.agcforge.videodownloader.helper.ads.NativeAdsHelper
import com.agcforge.videodownloader.ui.activities.ReportActivity
=======
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.agcforge.videodownloader.data.model.DownloadTask
import com.agcforge.videodownloader.databinding.FragmentHistoryBinding
import com.agcforge.videodownloader.helper.BannerAdsHelper
import com.agcforge.videodownloader.ui.adapter.HistoryAdapter
import com.agcforge.videodownloader.helper.BillingManager
import com.agcforge.videodownloader.helper.NativeAdsHelper
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444
import com.agcforge.videodownloader.utils.PreferenceManager
import com.agcforge.videodownloader.utils.showToast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var historyAdapter: HistoryAdapter
	private var historyNativeAdsHelper: NativeAdsHelper? = null

    private var bannerAdsHelper: BannerAdsHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager = PreferenceManager(requireContext())

        setupRecyclerView()
        observeHistory()
		observePremiumAdsState()

        binding.btnClearHistory.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                preferenceManager.clearHistory()
            }
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val historyList = preferenceManager.history.first()
                updateUiWithHistoryList(historyList)
                binding.swipeRefresh.isRefreshing = false
            }
        }

        setupAds()
    }

    private fun setupAds() {
        val act = activity ?: return
        bannerAdsHelper = BannerAdsHelper(act)

        bannerAdsHelper?.loadAndAttachBanner(binding.adsBanner)
    }
    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(
            onCopyClick = {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Video URL", it.originalUrl)
                clipboard.setPrimaryClip(clip)
                requireContext().showToast("URL copied!")

            },
            onShareClick = {
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_SUBJECT, it.title)
                    putExtra(Intent.EXTRA_TEXT, it.originalUrl)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, null)
                startActivity(shareIntent)

            },
<<<<<<< HEAD
            onReportClick = {
                val intent = Intent(requireContext(), ReportActivity::class.java).apply {
                    putExtra("EXTRA_SUBJECT", it.title)
                    putExtra("EXTRA_PLATFORM", it.platformType)
                    putExtra("EXTRA_URL", it.originalUrl)
                    putExtra("EXTRA_MESSAGE", it.errorMessage)
                }
                startActivity(intent)
            },
=======
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444
            onDeleteClick = { task ->
                lifecycleScope.launch {
                    preferenceManager.deleteHistoryItem(task)
                }
            }
        )
		applyPremiumAdsState(BillingManager.isPremiumNow())

        binding.rvHistory.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

	private fun observePremiumAdsState() {
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				BillingManager.isPremium.collect { isPremium ->
					applyPremiumAdsState(isPremium)
				}
			}
		}
	}

	private fun applyPremiumAdsState(isPremium: Boolean) {
		if (isPremium) {
			historyNativeAdsHelper?.destroy()
			historyNativeAdsHelper = null
			historyAdapter.enableAds(
				nativeAdsHelper = null,
				bannerAdsHelper = null,
				config = HistoryAdapter.AdInsertionConfig(startAfter = 0, interval = 0)
			)
			return
		}
		val act = activity ?: return
		if (historyNativeAdsHelper == null) historyNativeAdsHelper = NativeAdsHelper(act)
		historyAdapter.enableAds(
			nativeAdsHelper = historyNativeAdsHelper,
			slotType = HistoryAdapter.AdSlotType.NATIVE_SMALL,
			config = HistoryAdapter.AdInsertionConfig(startAfter = 3, interval = 8)
		)
	}

    private fun observeHistory() {
        binding.progressBar.visibility = View.VISIBLE
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				preferenceManager.history.collect { historyList ->
					_binding?.let {
						it.progressBar.visibility = View.GONE
						updateUiWithHistoryList(historyList)
					}
				}
			}
		}
    }

    private fun updateUiWithHistoryList(historyList: List<DownloadTask>) {
        val isHistoryEmpty = historyList.isEmpty()
        binding.tvEmpty.visibility = if (isHistoryEmpty) View.VISIBLE else View.GONE
        binding.rvHistory.visibility = if (isHistoryEmpty) View.GONE else View.VISIBLE
        binding.btnClearHistory.visibility = if (isHistoryEmpty) View.GONE else View.VISIBLE

		historyAdapter.submitTasks(historyList) // Show the most recent items at the top
    }

    override fun onDestroyView() {
        super.onDestroyView()
		historyNativeAdsHelper?.destroy()
		historyNativeAdsHelper = null
        _binding = null
    }
}
