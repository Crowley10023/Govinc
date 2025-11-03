package com.govinc.repository;

import com.govinc.entity.ProviderSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProviderSettingsRepository extends JpaRepository<ProviderSettings, Long> {
    List<ProviderSettings> findByProvider(String provider);
    ProviderSettings findFirstByProviderAndSettingKey(String provider, String settingKey);
}
