# frozen_string_literal: true

module TTY
  module Formats
    FORMATS = {
      classic: {
        interval: 10,
        frames: %w{| / - \\}
      },
      spin: {
        interval: 10,
        frames: %w{◴ ◷ ◶ ◵ }
      },
      spin_2: {
        interval: 10,
        frames: %w{◐ ◓ ◑ ◒ }
      },
      spin_3: {
        interval: 10,
        frames: %w{◰ ◳ ◲ ◱}
      },
      spin_4: {
        interval: 10,
        frames: %w{╫ ╪}
      },
      pulse: {
        interval: 10,
        frames: %w{⎺ ⎻ ⎼ ⎽ ⎼ ⎻}
      },
      pulse_2: {
        interval: 15,
        frames: %w{▁ ▃ ▅ ▆ ▇ █ ▇ ▆ ▅ ▃ }
      },
      pulse_3: {
        interval: 20,
        frames: '▉▊▋▌▍▎▏▎▍▌▋▊▉'
      },
      dots: {
        interval: 10,
        frames: %w{⠋ ⠙ ⠹ ⠸ ⠼ ⠴ ⠦ ⠧ ⠇ ⠏}
      },
      dots_2: {
        interval: 10,
        frames: %w{⣾ ⣽ ⣻ ⢿ ⡿ ⣟ ⣯ ⣷}
      },
      dots_3: {
        interval: 10,
        frames: %w{⠋ ⠙ ⠚ ⠞ ⠖ ⠦ ⠴ ⠲ ⠳ ⠓}
      },
      dots_4: {
        interval: 10,
        frames: %w{⠄ ⠆ ⠇ ⠋ ⠙ ⠸ ⠰ ⠠ ⠰ ⠸ ⠙ ⠋ ⠇ ⠆}
      },
      dots_5: {
        interval: 10,
        frames: %w{⠋ ⠙ ⠚ ⠒ ⠂ ⠂ ⠒ ⠲ ⠴ ⠦ ⠖ ⠒ ⠐ ⠐ ⠒ ⠓ ⠋}
      },
      dots_6: {
        interval: 10,
        frames: %w{⠁ ⠉ ⠙ ⠚ ⠒ ⠂ ⠂ ⠒ ⠲ ⠴ ⠤ ⠄ ⠄ ⠤ ⠴ ⠲ ⠒ ⠂ ⠂ ⠒ ⠚ ⠙ ⠉ ⠁}
      },
      dots_7: {
        interval: 10,
        frames: %w{⠈ ⠉ ⠋ ⠓ ⠒ ⠐ ⠐ ⠒ ⠖ ⠦ ⠤ ⠠ ⠠ ⠤ ⠦ ⠖ ⠒ ⠐ ⠐ ⠒ ⠓ ⠋ ⠉ ⠈}
      },
      dots_8: {
        interval: 10,
        frames: %w{⠁ ⠁ ⠉ ⠙ ⠚ ⠒ ⠂ ⠂ ⠒ ⠲ ⠴ ⠤ ⠄ ⠄ ⠤ ⠠ ⠠ ⠤ ⠦ ⠖ ⠒ ⠐ ⠐ ⠒ ⠓ ⠋ ⠉ ⠈ ⠈}
      },
      dots_9: {
        interval: 10,
        frames: %w{⢹ ⢺ ⢼ ⣸ ⣇ ⡧ ⡗ ⡏}
      },
      dots_10: {
        interval: 10,
        frames: %w{⢄ ⢂ ⢁ ⡁ ⡈ ⡐ ⡠}
      },
      dots_11: {
        interval: 10,
        frames: %w{⠁ ⠂ ⠄ ⡀ ⢀ ⠠ ⠐ ⠈}
      },
      arrow: {
        interval: 10,
        frames: %w{← ↖ ↑ ↗ → ↘ ↓ ↙ }
      },
      arrow_pulse: {
        interval: 10,
        frames: [
          "▹▹▹▹▹",
          "▸▹▹▹▹",
          "▹▸▹▹▹",
          "▹▹▸▹▹",
          "▹▹▹▸▹",
          "▹▹▹▹▸"
        ]
      },
      triangle: {
        interval: 10,
        frames: %w{◢ ◣ ◤ ◥}
      },
      arc: {
        interval: 10,
        frames: %w{ ◜ ◠ ◝ ◞ ◡ ◟ }
      },
      pipe: {
        interval: 10,
        frames: %w{ ┤ ┘ ┴ └ ├ ┌ ┬ ┐ }
      },
      bouncing: {
        interval: 10,
        frames: [
          "[    ]",
          "[   =]",
          "[  ==]",
          "[ ===]",
          "[====]",
          "[=== ]",
          "[==  ]",
          "[=   ]"
        ]
      },
      bouncing_ball: {
        interval: 10,
        frames: [
          "( ●    )",
          "(  ●   )",
          "(   ●  )",
          "(    ● )",
          "(     ●)",
          "(    ● )",
          "(   ●  )",
          "(  ●   )",
          "( ●    )",
          "(●     )"
        ]
      },
      bounce: {
        interval: 10,
        frames: %w{ ⠁ ⠂ ⠄ ⠂ }
      },
      box_bounce: {
        interval: 10,
        frames: %w{ ▌ ▀ ▐ ▄  }
      },
      box_bounce_2: {
        interval: 10,
        frames: %w{ ▖ ▘ ▝ ▗ }
      },
      star: {
        interval: 10,
        frames: %w{ ✶ ✸ ✹ ✺ ✹ ✷ }
      },
      toggle: {
        interval: 10,
        frames: %w{ ■ □ ▪ ▫ }
      },
      balloon: {
        interval: 10,
        frames: %w{ . o O @ * }
      },
      balloon_2: {
        interval: 10,
        frames: %w{. o O ° O o . }
      },
      flip: {
        interval: 10,
        frames: '-◡⊙-◠'
      },
      burger: {
        interval: 6,
        frames: %w{ ☱ ☲ ☴ }
      },
      dance: {
        interval: 10,
        frames: [">))'>", " >))'>", "  >))'>", "   >))'>", "    >))'>", "   <'((<", "  <'((<", " <'((<"]
      },
      shark: {
        interval: 10,
        frames: [
          "▐|\\____________▌",
          "▐_|\\___________▌",
          "▐__|\\__________▌",
          "▐___|\\_________▌",
          "▐____|\\________▌",
          "▐_____|\\_______▌",
          "▐______|\\______▌",
          "▐_______|\\_____▌",
          "▐________|\\____▌",
          "▐_________|\\___▌",
          "▐__________|\\__▌",
          "▐___________|\\_▌",
          "▐____________|\\▌",
          "▐____________/|▌",
          "▐___________/|_▌",
          "▐__________/|__▌",
          "▐_________/|___▌",
          "▐________/|____▌",
          "▐_______/|_____▌",
          "▐______/|______▌",
          "▐_____/|_______▌",
          "▐____/|________▌",
          "▐___/|_________▌",
          "▐__/|__________▌",
          "▐_/|___________▌",
          "▐/|____________▌"
        ]
      },
      pong: {
        interval: 10,
        frames: [
          "▐⠂       ▌",
          "▐⠈       ▌",
          "▐ ⠂      ▌",
          "▐ ⠠      ▌",
          "▐  ⡀     ▌",
          "▐  ⠠     ▌",
          "▐   ⠂    ▌",
          "▐   ⠈    ▌",
          "▐    ⠂   ▌",
          "▐    ⠠   ▌",
          "▐     ⡀  ▌",
          "▐     ⠠  ▌",
          "▐      ⠂ ▌",
          "▐      ⠈ ▌",
          "▐       ⠂▌",
          "▐       ⠠▌",
          "▐       ⡀▌",
          "▐      ⠠ ▌",
          "▐      ⠂ ▌",
          "▐     ⠈  ▌",
          "▐     ⠂  ▌",
          "▐    ⠠   ▌",
          "▐    ⡀   ▌",
          "▐   ⠠    ▌",
          "▐   ⠂    ▌",
          "▐  ⠈     ▌",
          "▐  ⠂     ▌",
          "▐ ⠠      ▌",
          "▐ ⡀      ▌",
          "▐⠠       ▌"
        ]
      }
    }
  end # Formats
end # TTY
