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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.agcforge.videodownloader.data.model.DownloadTask
import com.agcforge.videodownloader.databinding.FragmentHistoryBinding
import com.agcforge.videodownloader.ui.adapter.HistoryAdapter
import com.agcforge.videodownloader.utils.PreferenceManager
import com.agcforge.videodownloader.utils.showToast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var historyAdapter: HistoryAdapter

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
            onDeleteClick = { task ->
                lifecycleScope.launch {
                    preferenceManager.deleteHistoryItem(task)
                }
            }
        )

        binding.rvHistory.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
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

        historyAdapter.submitList(historyList) // Show the most recent items at the top
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
