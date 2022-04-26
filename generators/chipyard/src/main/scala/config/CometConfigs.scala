package chipyard

import freechips.rocketchip.config.{Config, Parameters}
import freechips.rocketchip.diplomacy.{AsynchronousCrossing, SynchronousCrossing}
import freechips.rocketchip.devices.tilelink.{BootROMLocated, MaskROMLocated, MaskROMParams}
import freechips.rocketchip.tile.{XLen, RocketTileParams}
import freechips.rocketchip.rocket.{RocketCoreParams, ICacheParams, DCacheParams, MulDivParams}
import freechips.rocketchip.subsystem.{RocketTilesKey, RocketCrossingKey, RocketCrossingParams, TileMasterPortParams,
  FrontBusKey, SystemBusKey, SystemBusParams, CacheBlockBytes, InSubsystem}
import sifive.blocks.devices.uart._

class WithComet extends Config((site, here, up) => {
  case BuildTop => (p: Parameters) => new CometCPUComplex()(p)
  case BootROMLocated(x) => None
})

class WithCometUART(baudrate: BigInt = 115200) extends Config((site, here, up) => {
  case PeripheryUARTKey => Seq(
    UARTParams(address = 0x34000000L, nTxEntries = 256, nRxEntries = 256, initBaudRate = baudrate))
})

class CometBaseConfig extends Config(
  new WithComet ++

  // Test harness features
  new chipyard.harness.WithUARTAdapter ++                       // add UART adapter to display UART on stdout, if uart is present
  new chipyard.harness.WithSimAXIMem ++                         // don't use DRAM sim because it doesn't support > 64 bits
  new chipyard.harness.WithSimDebug ++                          // add SimJTAG or SimDTM adapters if debug module is enabled
  new chipyard.harness.WithGPIOTiedOff ++                       // tie-off chiptop GPIOs, if GPIOs are present
  new chipyard.harness.WithSimSPIFlashModel ++                  // add simulated SPI flash memory, if SPI is enabled
  new chipyard.harness.WithSimAXIMMIO ++                        // add SimAXIMem for axi4 mmio port, if enabled
  new chipyard.harness.WithTieOffInterrupts ++                  // tie-off interrupt ports, if present
  new chipyard.harness.WithTieOffL2FBusAXI ++                   // tie-off external AXI4 initiator, if present
  new chipyard.harness.WithClockAndResetFromHarness ++

  // IO binders
  new chipyard.iobinders.WithAXI4MemPunchthrough ++
  new chipyard.iobinders.WithAXI4MMIOPunchthrough ++
  new chipyard.iobinders.WithL2FBusAXI4Punchthrough ++
  new chipyard.iobinders.WithBlockDeviceIOPunchthrough ++
  new chipyard.iobinders.WithNICIOPunchthrough ++
  new chipyard.iobinders.WithDebugIOCells ++
  new chipyard.iobinders.WithUARTIOCells ++
  new chipyard.iobinders.WithGPIOCells ++
  new chipyard.iobinders.WithUARTIOCells ++
  new chipyard.iobinders.WithSPIIOCells ++
  new chipyard.iobinders.WithTraceIOPunchthrough ++
  new chipyard.iobinders.WithExtInterruptIOCells ++
  new chipyard.iobinders.WithDividerOnlyClockGenerator ++

  // Design configuration
  new freechips.rocketchip.subsystem.WithEdgeDataBits(128) ++    // 128-bit mbus and mmio
  new chipyard.WithCometUART ++                                  // add a UART
  new chipyard.config.WithL2TLBs(1024) ++                        // use L2 TLBs
  new chipyard.config.WithNoSubsystemDrivenClocks ++             // drive the subsystem diplomatic clocks from ChipTop instead of using implicit clocks
  new chipyard.config.WithInheritBusFrequencyAssignments ++      // Unspecified clocks within a bus will receive the bus frequency if set
  new chipyard.config.WithPeripheryBusFrequencyAsDefault ++      // Unspecified frequencies with match the pbus frequency (which is always set)
  new chipyard.config.WithMemoryBusFrequency(100.0) ++           // Default 100 MHz mbus
  new chipyard.config.WithPeripheryBusFrequency(100.0) ++        // Default 100 MHz pbus
  new freechips.rocketchip.subsystem.WithJtagDTM ++              // set the debug module to expose a JTAG port
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(4) ++ // no external interrupts
  new chipyard.WithMulticlockCoherentBusTopology ++              // hierarchical buses including mbus+l2
  new freechips.rocketchip.system.BaseConfig
)

class CometCoreConfig extends Config((site, here, up) => {
  case XLen => 32
  case SystemBusKey => SystemBusParams(
    beatBytes = 16,
    blockBytes = site(CacheBlockBytes)
  )
  case RocketTilesKey => List(RocketTileParams(
      core = RocketCoreParams(
        useVM = false,
        mulDiv = Some(MulDivParams(mulUnroll = 8, mulEarlyOut = true, divEarlyOut = true))),
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 256,
        nWays = 4,
        nMSHRs = 0,
        blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        nSets = 256,
        nWays = 4,
        itimAddr = Some(0x300000),
        blockBytes = site(CacheBlockBytes)))))
  case RocketCrossingKey => List(RocketCrossingParams(
    crossingType = SynchronousCrossing(),
    master = TileMasterPortParams()
  ))
  case MaskROMLocated(InSubsystem) => Seq(MaskROMParams(
    address = BigInt(0x10000),
    name = "CometROM",
    depth = 1024,
    width = 32
  ))
})

class CometConfig extends Config(
  new freechips.rocketchip.subsystem.WithDefaultMMIOPort ++  // add default external master port
  new freechips.rocketchip.subsystem.WithDefaultSlavePort ++ // add default external slave port
  new CometCoreConfig ++
  new CometBaseConfig
)

