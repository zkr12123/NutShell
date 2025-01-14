if {[llength $argv] > 0} {
  set project_name [lindex $argv 0]
  set s [split $project_name -]
  set prj [lindex $s 0]
  set brd [lindex $s 1]
} else {
  puts "project full name is not given!"
  return 1
}

if {[llength $argv] > 1} {
  set standalone [lindex $argv 1]
} else {
  puts "standalone mode is not given!"
  return 1
}

proc add_bd {tcl_file} {
  source ${tcl_file}
  save_bd_design
  close_bd_design $design_name
  set_property synth_checkpoint_mode Hierarchical [get_files *${design_name}.bd]
}

set fpga_dir    ${script_dir}/../..
set project_dir ${script_dir}/build/$project_name
set rtl_dir     ${script_dir}/rtl
set lib_dir     ${fpga_dir}/lib
set bd_dir      ${script_dir}/bd
set constr_dir  ${script_dir}/constr
set data_dir    ${script_dir}/data
set ip_dir      ${script_dir}/ip

create_project $project_name -force -dir $project_dir/ -part ${device}
if {[info exists board]} {
  set_property board_part $board [current_project]
}

# lib files
set inc_files [list \
  "[file normalize "${lib_dir}/include/axi.vh"]" \
]
add_files -norecurse -fileset sources_1 $inc_files
set_property is_global_include true [get_files $inc_files]

# Add files for nutshell
# lappend src_files "[file normalize "${fpga_dir}/../build/TopMain.v"]" \
#                   "[file normalize "${fpga_dir}/../build/DifftestRunaheadEvent.v"]" \
#                   "[file normalize "${fpga_dir}/../build/DifftestRunaheadRedirectEvent.v"]"

lappend src_files "[file normalize "${fpga_dir}/../build/DifftestRunaheadEvent.v"]" \
                  "[file normalize "${fpga_dir}/../build/DifftestRunaheadRedirectEvent.v"]"

foreach v_file [glob "${fpga_dir}/../build/module_sources/*.v"] {
  lappend src_files "[file normalize $v_file]"
}

# Fix Vivado synthesis missing file error
lappend src_files "[file normalize "${fpga_dir}/array_ext.v"]"

add_files -norecurse -fileset sources_1 $src_files
set_property include_dirs "${fpga_dir}/../build/module_sources/" [get_filesets sources_1]

# Mark file type of difftest files as SystemVerilog to support DPI statements
set_property file_type SystemVerilog -objects [get_files -of_objects [get_filesets sources_1] [list \
  "*/DifftestRunaheadRedirectEvent.v" \
  "*/DifftestRunaheadEvent.v" \
]]

if {[info exists xdc_files]} {
  add_files -norecurse -fileset constrs_1 $xdc_files
}

# Block Designs
if {${standalone} == "true"} {
  add_bd ${bd_dir}/standalone.tcl
  make_wrapper -files [get_files *system_top.bd] -top
  add_files -norecurse -fileset sources_1 $project_dir/$project_name.srcs/sources_1/bd/system_top/hdl/system_top_wrapper.v
  set topmodule system_top_wrapper
} else {
  # add_bd ${fpga_dir}/NutShell.tcl
  add_bd ${fpga_dir}/NutShell_peripheral.tcl
  add_bd ${bd_dir}/arm.tcl
  set topmodule system_top
}

# setting top module for FPGA flow and simulation flow
set_property "top" $topmodule [current_fileset]

# setting Synthesis options
set_property strategy {Vivado Synthesis defaults} [get_runs synth_1]
# keep module port names in the netlist
set_property STEPS.SYNTH_DESIGN.ARGS.FLATTEN_HIERARCHY {none} [get_runs synth_1]

# setting Implementation options
set_property steps.phys_opt_design.is_enabled true [get_runs impl_1]

# update compile order 
update_compile_order -fileset sources_1

# PR Flow
# set pr_flow "true"
# if {${pr_flow} == "true"} {
#   source ${fpga_dir}/pr_script/NutShell_pr.tcl
# }
