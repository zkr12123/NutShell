
package nutcore

import chisel3._
import chisel3.util._ 
import top.Settings


object RVCustom extends HasInstrType {
    def ADDDEC     = BitPat("b0000000_?????_?????_???_?????_0001011") //funct7_src2_src1_funct3_rd_custom_0
    def SIGMOID    = BitPat("b0000000_?????_?????_???_?????_0101011")

    val table = Array(
        ADDDEC            -> List(InstrR, FuType.custom, CustomFuOpType.default),
        SIGMOID           -> List(InstrR, FuType.custom, CustomFuOpType.default)
    ) // Decode table used in IDU

    def customTable(CustomFU: String) = CustomFU match {
        case "Sigmoid" => Array(SIGMOID  -> List(InstrR, FuType.custom, CustomFuOpType.default))
        case "AddDec"  => Array(ADDDEC   -> List(InstrR, FuType.custom, CustomFuOpType.default))
    }

}