import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.core.HostEntity;

import java.util.List;

/**
 * Kebijakan alokasi VM terdistribusi merata (Uniform Allocation Policy).
 * Membatasi jumlah maksimum VM per host fisik agar seluruh host
 * aktif terpakai dan tidak ada host fisik yang sia-sia / menganggur.
 */
public class VmAllocationPolicyUniform extends VmAllocationPolicy {

    private final int maxVmsPerHost;

    public VmAllocationPolicyUniform(
        List<? extends HostEntity> hostList,
        int maxVmsPerHost
    ) {
        super(hostList);
        this.maxVmsPerHost = Math.max(1, maxVmsPerHost);
    }

    @Override
    public HostEntity findHostForGuest(GuestEntity guest) {
        for (HostEntity host : getHostList()) {
            if (host.getGuestList().size() < maxVmsPerHost
                && host.isSuitableForGuest(guest)) {
                return host;
            }
        }
        return null;
    }

    public int getMaxVmsPerHost() {
        return maxVmsPerHost;
    }
}
