package com.video.vibetube.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.video.vibetube.R
import com.video.vibetube.activity.MainActivity
import com.video.vibetube.adapters.MoreOption
import com.video.vibetube.adapters.MoreOptionsAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.core.graphics.drawable.toDrawable
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.core.graphics.toColorInt

@SuppressLint("ResourceType")
class MoreBottomSheetDialog : BottomSheetDialogFragment(R.style.CustomBottomSheetDialogTheme) {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { d ->
            val bottomSheet = (d as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background = ContextCompat.getDrawable(requireContext(), R.drawable.bottom_sheet_background)
        }
        dialog.window?.setBackgroundDrawable("#80000000".toColorInt().toDrawable())
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_more_options, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.optionsRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        recyclerView.setHasFixedSize(true)

        val options = listOf(
            MoreOption(R.drawable.ic_diy, "DIY", 4),
            MoreOption(R.drawable.ic_video_placeholder, "Vlogs", 5),
            MoreOption(R.drawable.ic_music_note, "Spiritual", 6),
            MoreOption(R.drawable.ic_school, "Science", 7)
        )

        val adapter = MoreOptionsAdapter(options) { position ->
            (activity as? MainActivity)?.navigateToFragment(position)
            dismiss()
        }

        recyclerView.adapter = adapter
    }
}