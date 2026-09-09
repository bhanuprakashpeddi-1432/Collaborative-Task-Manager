import type { UserPresencePayload } from '@/types';

interface PresenceAvatarsProps {
  activeUsers: Record<string, UserPresencePayload>;
}

export function PresenceAvatars({ activeUsers }: PresenceAvatarsProps) {
  const usersList = Object.values(activeUsers);

  if (usersList.length === 0) return null;

  return (
    <div className="flex items-center gap-2">
      <span className="text-xs text-gray-500 font-medium mr-1">Active Now</span>
      <div className="flex -space-x-2">
        {usersList.map((presence) => (
          <div
            key={presence.userId}
            className="relative group"
            title={`${presence.fullName} is ${presence.action.toLowerCase()}`}
          >
            {presence.avatarUrl ? (
              <img
                src={presence.avatarUrl}
                alt={presence.fullName}
                className={`w-8 h-8 rounded-full border-2 border-white shadow-sm object-cover ${
                  presence.action === 'EDITING' ? 'ring-2 ring-orange-400 ring-offset-1' : ''
                }`}
              />
            ) : (
              <div 
                className={`w-8 h-8 rounded-full border-2 border-white shadow-sm flex items-center justify-center text-xs font-bold text-white ${
                  presence.action === 'EDITING' 
                    ? 'bg-orange-500 ring-2 ring-orange-400 ring-offset-1' 
                    : 'bg-indigo-500'
                }`}
              >
                {presence.fullName.charAt(0).toUpperCase()}
              </div>
            )}
            
            {/* Status Indicator */}
            <span 
              className={`absolute bottom-0 right-0 w-2.5 h-2.5 rounded-full border border-white ${
                presence.action === 'EDITING' ? 'bg-orange-400' : 'bg-green-400'
              }`} 
            />
          </div>
        ))}
      </div>
    </div>
  );
}
