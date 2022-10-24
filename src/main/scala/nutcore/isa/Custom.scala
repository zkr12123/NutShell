
package nutcore

import chisel3._
import chisel3.util._ 


object RVCustom extends HasInstrType {
    def ADDDEC     = BitPat("0000000_00000_?????_???_?????_0001011") //funct7_src2_src1_funct3_rd_custom_0

    val table = Array(
        ADDDEC            -> List(InstrR, FuType.custom, CustomFuOpType.default)
    ) // Decode table used in IDU
}