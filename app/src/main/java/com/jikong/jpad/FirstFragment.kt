package com.jikong.jpad

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.jikong.jpad.databinding.FragmentFirstBinding
import com.jikong.jpad.databinding.ItemMemoCardBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private var selectedFile: File? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonRefreshFiles.setOnClickListener {
            loadTxtFiles()
            Toast.makeText(requireContext(), R.string.files_refreshed, Toast.LENGTH_SHORT).show()
        }



        binding.buttonCloseDetail.setOnClickListener {
            binding.cardMemoDetail.visibility = View.GONE
            selectedFile = null
        }

        binding.buttonSaveFile.setOnClickListener {
            selectedFile?.let { file ->
                val newContent = binding.editDetailContent.text.toString()
                runCatching {
                    file.writeText(newContent)
                    Toast.makeText(requireContext(), R.string.file_saved, Toast.LENGTH_SHORT).show()
                    val sizeKb = file.length() / 1024.0
                    binding.textDetailInfo.text = "경로: ${file.absolutePath} (%.2f KB)".format(sizeKb)
                    loadTxtFiles()
                }.onFailure {
                    Toast.makeText(requireContext(), "저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.buttonDeleteFile.setOnClickListener {
            selectedFile?.let { file ->
                if (file.exists() && file.delete()) {
                    Toast.makeText(requireContext(), R.string.file_deleted, Toast.LENGTH_SHORT).show()
                    binding.cardMemoDetail.visibility = View.GONE
                    selectedFile = null
                    loadTxtFiles()
                }
            }
        }

        ensureSampleFileExists()
        loadTxtFiles()
    }

    private fun getSaveDir(): File? {
        val ctx = context ?: return null
        val baseDir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val jpadDir = File(baseDir, "Jpad_Saves")
        if (!jpadDir.exists()) {
            jpadDir.mkdirs()
        }
        return jpadDir
    }

    private fun getAppFolders(): List<File> {
        val folders = mutableListOf<File>()
        context?.filesDir?.let {
            folders.add(it)
            folders.add(File(it, "Jpad_Saves"))
        }
        context?.getExternalFilesDir(null)?.let {
            folders.add(it)
            folders.add(File(it, "Jpad_Saves"))
        }
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir != null) {
            folders.add(File(downloadsDir, "Jpad_Saves"))
        }
        return folders
    }

    private fun ensureSampleFileExists() {
        val saveDir = getSaveDir() ?: return
        val sampleFile = File(saveDir, "sample_note.txt")
    }

    fun createSampleTxtFile() {
        val saveDir = getSaveDir() ?: return
        val count = (saveDir.listFiles { _, name -> name.lowercase().endsWith(".txt") }?.size ?: 0) + 1
        val fileName = "memo_$count.txt"
        val file = File(saveDir, fileName)
        file.writeText("")
        Toast.makeText(requireContext(), "$fileName 생성됨", Toast.LENGTH_SHORT).show()
        loadTxtFiles()
        showMemoDetail(file)
    }

    private fun loadTxtFiles() {
        val files = mutableListOf<File>()
        getAppFolders().forEach { folder ->
            if (folder.exists() && folder.isDirectory) {
                folder.listFiles { _, name -> name.lowercase().endsWith(".txt") }?.let {
                    files.addAll(it)
                }
            }
        }
        val txtFiles = files.distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }

        if (txtFiles.isEmpty()) {
            binding.recyclerMemos.visibility = View.GONE
            binding.textEmptyMemos.visibility = View.VISIBLE
            binding.cardMemoDetail.visibility = View.GONE
        } else {
            binding.recyclerMemos.visibility = View.VISIBLE
            binding.textEmptyMemos.visibility = View.GONE

            val adapter = MemoAdapter(txtFiles) { file ->
                showMemoDetail(file)
            }
            binding.recyclerMemos.adapter = adapter
        }
    }

    private fun showMemoDetail(file: File) {
        selectedFile = file
        binding.cardMemoDetail.visibility = View.VISIBLE
        binding.textDetailTitle.text = file.name

        val sizeKb = file.length() / 1024.0
        binding.textDetailInfo.text = "경로: ${file.absolutePath} (%.2f KB)".format(sizeKb)

        runCatching {
            binding.editDetailContent.setText(file.readText())
        }.onFailure {
            binding.editDetailContent.setText("파일을 읽는 도중 오류가 발생했습니다: ${it.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class MemoAdapter(
        private val memoFiles: List<File>,
        private val onItemClick: (File) -> Unit
    ) : RecyclerView.Adapter<MemoAdapter.MemoViewHolder>() {

        class MemoViewHolder(val binding: ItemMemoCardBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoViewHolder {
            val binding = ItemMemoCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return MemoViewHolder(binding)
        }

        override fun onBindViewHolder(holder: MemoViewHolder, position: Int) {
            val file = memoFiles[position]
            val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())

            holder.binding.textMemoTitle.text = file.name
            holder.binding.textMemoDate.text = dateFormat.format(Date(file.lastModified()))

            runCatching {
                val fullText = file.readText()
                val snippet = fullText.lines().filter { it.isNotBlank() }.take(3).joinToString("\n")
                holder.binding.textMemoSnippet.text = snippet.ifEmpty { "(내용 없음)" }
            }.onFailure {
                holder.binding.textMemoSnippet.text = "(읽기 오류)"
            }

            holder.itemView.setOnClickListener {
                onItemClick(file)
            }
        }

        override fun getItemCount(): Int = memoFiles.size
    }
}
