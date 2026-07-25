import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig(() => {
  if (!process.env.VITE_API_URL) {
    throw new Error('VITE_API_URL must be loaded through scripts/environment')
  }

  return {
    plugins: [react()],
  }
})
