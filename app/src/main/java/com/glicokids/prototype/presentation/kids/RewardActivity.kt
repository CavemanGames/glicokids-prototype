package com.glicokids.prototype.presentation.kids

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.glicokids.prototype.data.local.AppPreferences
import com.glicokids.prototype.databinding.ActivityRewardBinding
import com.glicokids.prototype.util.SoundHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RewardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRewardBinding

    @Inject lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRewardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SoundHelper.play(this, SoundHelper.SoundEvent.MEDAL_EARNED, prefs)

        binding.btnBackToBase.setOnClickListener {
            finish()
        }
    }
}
