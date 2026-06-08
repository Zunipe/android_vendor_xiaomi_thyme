package vendor.zunipe.perf;

@VintfStability
interface IPerf {
    int getCpuFreq(in int core);
    int getGpuFreq();
    int perfLockAcq(int handle, int duration, in int[] list, int numArgs);
    int perfLockRel(int handle);
}
